/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2014 Geomatys.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.constellation.scheduler;


import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.sis.util.logging.Logging;
import org.constellation.admin.SpringHelper;
import org.constellation.dto.process.TaskStatus;
import org.constellation.api.TaskState;
import org.constellation.business.IProcessBusiness;
import org.constellation.dto.process.Task;
import org.constellation.util.ParamUtilities;
import org.geotoolkit.process.ProcessEvent;
import org.geotoolkit.process.ProcessListener;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.util.InternationalString;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.UUID;
import org.constellation.api.CstlJobListener;
import org.constellation.exception.ConstellationException;
import org.geotoolkit.processing.quartz.ProcessJob;
import org.geotoolkit.processing.quartz.ProcessJobDetail;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

/**
 * Quartz Job listener attaching a listener on geotoolkit processes to track their state.
 *
 * @author Johann Sorel (Geomatys)
 * @author Christophe Mourette (Geomatys)
 */
public class QuartzJobListener implements JobListener, CstlJobListener {

    private static final Logger LOGGER = Logging.getLogger("org.constellation.scheduler");
    private static final int ROUND_SCALE = 2;

    public static final String PROPERTY_TASK = "task";
    private IProcessBusiness processBusiness;

    public QuartzJobListener() {
    }

    ////////////////////////////////////////////////////////////////////////////
    // Quartz listener /////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public String getName() {
        return "ConstellationJobTracker";
    }

    @Override
    public synchronized void jobToBeExecuted(JobExecutionContext jec) {
        if (processBusiness == null) {
            this.processBusiness = SpringHelper.getBean(IProcessBusiness.class);
        }

        final Job job = jec.getJobInstance();
        if(!(job instanceof ProcessJob)) return;
        final ProcessJob pj = (ProcessJob) job;
        final ProcessJobDetail detail = (ProcessJobDetail) jec.getJobDetail();
        final QuartzTask quartzTask = (QuartzTask) detail.getJobDataMap().get(QuartzJobListener.PROPERTY_TASK);
        String jobId = detail.getKey().toString() + '_' + UUID.randomUUID().toString();
        pj.setJobId(jobId);
        SpringHelper.executeInTransaction(new TransactionCallback<Object>() {
            @Override
            public Object doInTransaction(TransactionStatus status) {
                final Task taskEntity = new Task();
                taskEntity.setIdentifier(jobId);
                taskEntity.setState(TaskState.PENDING.name());
                taskEntity.setTaskParameterId(quartzTask.getTaskParameterId());
                taskEntity.setOwner(quartzTask.getUserId());
                taskEntity.setType(""); // TODO
                try {
                    processBusiness.addTask(taskEntity);
                } catch (ConstellationException ex) {
                    LOGGER.log(Level.WARNING, ex.getMessage(), ex);
                }

                final ProcessListener listener = new StateListener(taskEntity.getIdentifier(), quartzTask.getTitle() );
                pj.addListener(listener);
                LOGGER.log(Level.INFO, "Run task "+taskEntity.getIdentifier());
                return null;
            }
        });
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext jec) {
        System.out.println("veto");
    }

    @Override
    public void jobWasExecuted(JobExecutionContext jec, JobExecutionException jee) {
       if (jee != null) {
            LOGGER.warning("Error after job execution:" + jee.getMessage());
            final ProcessJobDetail detail = (ProcessJobDetail) jec.getJobDetail();
            final ProcessJob pj = (ProcessJob) jec.getJobInstance();
            final QuartzTask quartzTask = (QuartzTask) detail.getJobDataMap().get(QuartzJobListener.PROPERTY_TASK);

            SpringHelper.executeInTransaction(new TransactionCallback<Object>() {
                @Override
                public Object doInTransaction(TransactionStatus status) {
                    final Task taskEntity = processBusiness.getTask(pj.getJobId());

                    taskEntity.setState(TaskState.FAILED.name());
                    taskEntity.setDateEnd(System.currentTimeMillis());
                    taskEntity.setMessage(jee.getMessage() + " cause : " + printException(jee));
                    try {
                        //update in database
                        processBusiness.updateTask(taskEntity);
                    } catch (ConstellationException ex) {
                        LOGGER.log(Level.WARNING, ex.getMessage(), ex);
                    }

                    //send event
                    final TaskStatus taskStatus = new TaskStatus(taskEntity, quartzTask.getTitle());
                    SpringHelper.sendEvent(taskStatus);
                    return null;
                }
            });

        } else {
           final ProcessJob pj = (ProcessJob) jec.getJobInstance();
           LOGGER.info(pj.getJobId() + " Process execution finished");
       }
    }

    /**
     * Print an exception. Format : "message stacktrace"
     *
     * @param exception
     * @return
     */
    private static String printException(Exception exception) {
        StringWriter errors = new StringWriter();
        if (exception != null) {
            errors.append(exception.getMessage()).append('\n');
            exception.printStackTrace(new PrintWriter(errors));
        }
        return errors.toString();
    }

    ////////////////////////////////////////////////////////////////////////////
    // Geotk process listener //////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Catch process events and set them in the TaskState.
     */
    private static class StateListener implements ProcessListener{

        private final String title;
        private final Task taskEntity;
        private IProcessBusiness processBusiness;

        /** Used to store eventual warnings process could send us. */
        private final ArrayList<ProcessEvent> warnings = new ArrayList<>();

        public StateListener(String taskId, String title) {
            if (processBusiness == null) {
                this.processBusiness = SpringHelper.getBean(IProcessBusiness.class);
            }
            this.taskEntity = processBusiness.getTask(taskId);
            this.title = title;
        }

        @Override
        public void started(ProcessEvent event) {
            taskEntity.setState(TaskState.RUNNING.name());
            taskEntity.setDateStart(System.currentTimeMillis());
            taskEntity.setMessage(toString(event.getTask()));
            roundProgression(event);
            updateTask(taskEntity);
        }

        @Override
        public void progressing(ProcessEvent event) {
            taskEntity.setState(TaskState.RUNNING.name());
            taskEntity.setMessage(toString(event.getTask()));
            roundProgression(event);

            ParameterValueGroup output = event.getOutput();
            if (output != null) {
                try {
                    taskEntity.setTaskOutput(ParamUtilities.writeParameterJSON(output));
                } catch (JsonProcessingException e) {
                    LOGGER.log(Level.WARNING, "Process output serialization failed", e);
                }
            }

            if (event.getException() != null) {
                warnings.add(event);
            }

            updateTask(taskEntity);
        }

        @Override
        public void paused(ProcessEvent event) {
            taskEntity.setState(TaskState.PAUSED.name());
            taskEntity.setMessage(toString(event.getTask()));
            roundProgression(event);
            updateTask(taskEntity);
        }

        @Override
        public void resumed(ProcessEvent event) {
            taskEntity.setState(TaskState.RUNNING.name());
            taskEntity.setMessage(toString(event.getTask()));
            roundProgression(event);
            updateTask(taskEntity);
        }

        @Override
        public void completed(ProcessEvent event) {
            taskEntity.setDateEnd(System.currentTimeMillis());
            taskEntity.setMessage(toString(event.getTask()));
            roundProgression(event);

            ParameterValueGroup output = event.getOutput();
            if (output != null) {
                try {
                    taskEntity.setTaskOutput(ParamUtilities.writeParameterJSON(output));
                } catch (JsonProcessingException e) {
                    LOGGER.log(Level.WARNING, "Process output serialization failed", e);
                }
            }

            // If a warning occurred, send exception to the user.
            if (!warnings.isEmpty()) {
                taskEntity.setState(TaskState.WARNING.name());
                taskEntity.setMessage(processWarningMessage());
            } else {
                taskEntity.setState(TaskState.SUCCEED.name());
            }

            updateTask(taskEntity);
        }


        @Override
        public void failed(ProcessEvent event) {
            taskEntity.setState(TaskState.FAILED.name());
            taskEntity.setDateEnd(System.currentTimeMillis());

            final Exception exception = event.getException();
            final String exceptionStr = printException(exception);
            taskEntity.setMessage(toString(event.getTask()) + " cause : " + exceptionStr);
            //taskEntity.setProgress((double) event.getProgress());
            updateTask(taskEntity);
        }

        @Override
        public void dismissed(ProcessEvent event) {
            taskEntity.setState(TaskState.CANCELLED.name());
            taskEntity.setDateEnd(System.currentTimeMillis());

            final Exception exception = event.getException();
            final String exceptionStr = printException(exception);
            taskEntity.setMessage(toString(event.getTask()) + " cause : " + exceptionStr);
            //taskEntity.setProgress((double) event.getProgress());
            updateTask(taskEntity);
        }

        private void updateTask(Task taskEntity) {
            if (processBusiness == null) {
                this.processBusiness = SpringHelper.getBean(IProcessBusiness.class);
            }
            try {
                //update in database
                processBusiness.updateTask(taskEntity);
            } catch (ConstellationException ex) {
                LOGGER.log(Level.WARNING, ex.getMessage(), ex);
            }

            //send event
            final TaskStatus taskStatus = new TaskStatus(taskEntity, title);
            SpringHelper.sendEvent(taskStatus);
        }

        /**
         * Format :
         * "
         * Task1 description : exceptionMessage
         * stacktrace
         *
         * Task2 description : exceptionMessage
         * stacktrace
         * "
         * @return
         */
        private String processWarningMessage() {
            final StringBuilder warningStr = new StringBuilder();
            for (ProcessEvent warning : warnings) {
                warningStr.append(warning.getTask().toString()).append(" : ");
                warningStr.append(printException(warning.getException()));
                warningStr.append('\n');
            }
            return warningStr.toString();
        }

        /**
         * Round event progression value to {@link #ROUND_SCALE} before
         * set to taskEntity object.
         *
         * @param event ProcessEvent
         */
        private void roundProgression(ProcessEvent event) {
            if (!Float.isNaN(event.getProgress()) && !Float.isInfinite(event.getProgress())) {
                BigDecimal progress = new BigDecimal(event.getProgress());
                progress = progress.setScale(ROUND_SCALE, BigDecimal.ROUND_HALF_UP);
                taskEntity.setProgress(progress.doubleValue());
            }
        }

        private String toString(InternationalString str){
            if(str==null){
                return "";
            }else{
                return str.toString();
            }
        }

    }
}
