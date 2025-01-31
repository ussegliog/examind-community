/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2018 Geomatys.
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
package com.examind.repository.filesystem;

import static com.examind.repository.filesystem.FileSystemUtilities.*;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import org.constellation.dto.Layer;
import org.constellation.dto.Style;
import org.constellation.exception.ConstellationPersistenceException;
import org.constellation.repository.LayerRepository;
import org.constellation.repository.StyleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
@Component
public class FileSystemLayerRepository extends AbstractFileSystemRepository implements LayerRepository {

    private final Map<Integer, Layer> byId = new HashMap<>();
    private final Map<Integer, List<Layer>> byService = new HashMap<>();
    private final Map<Integer, List<Layer>> byData = new HashMap<>();
    private final Map<Integer, List<QName>> byServiceName = new HashMap<>();

    private final Map<String,  Layer> byServiceIdLayerName = new HashMap<>();
    private final Map<String,  Layer> byServiceIdLayerNameNmsp = new HashMap<>();
    private final Map<String,  Layer> byServiceIdAlias = new HashMap<>();
    private final Map<String,  Layer> byServiceIdDataId = new HashMap<>();

    @Autowired
    private StyleRepository styleRepository;

    public FileSystemLayerRepository() {
        super(Layer.class);
        load();
    }

    private void load() {
        try {

            Path layerDir = getDirectory(LAYER_DIR);
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(layerDir)) {
                for (Path layerFile : directoryStream) {
                    Layer layer = (Layer) getObjectFromPath(layerFile, pool);
                    byId.put(layer.getId(), layer);

                    if (!byData.containsKey(layer.getDataId())) {
                        List<Layer> layers = new ArrayList<>();
                        layers.add(layer);
                        byData.put(layer.getDataId(), layers);
                    } else {
                        byData.get(layer.getDataId()).add(layer);
                    }

                    if (!byService.containsKey(layer.getService())) {
                        List<Layer> layers = new ArrayList<>();
                        layers.add(layer);
                        byService.put(layer.getService(), layers);
                    } else {
                        byService.get(layer.getService()).add(layer);
                    }

                    if (!byServiceName.containsKey(layer.getService())) {
                        List<QName> layers = new ArrayList<>();
                        layers.add(new QName(layer.getNamespace(), layer.getName()));
                        byServiceName.put(layer.getService(), layers);
                    } else {
                        byServiceName.get(layer.getService()).add(new QName(layer.getNamespace(), layer.getName()));
                    }

                    String serviceIdLayerName = layer.getService() + '-' + layer.getName();
                    byServiceIdLayerName.put(serviceIdLayerName, layer);

                    String serviceIdLayerNameNmsp = layer.getService() + '-' + layer.getName() + '-' + layer.getNamespace();
                    byServiceIdLayerNameNmsp.put(serviceIdLayerNameNmsp, layer);

                    String serviceIdAlias = layer.getService() + '-' + layer.getAlias();
                    byServiceIdAlias.put(serviceIdAlias, layer);

                    String serviceIdDataId = layer.getService() + "-" + layer.getDataId();
                    byServiceIdDataId.put(serviceIdDataId, layer);

                    if (layer.getId() >= currentId) {
                        currentId = layer.getId() +1;
                    }
                }
            }

        } catch (IOException | JAXBException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public List<Layer> findAll() {
        return new ArrayList<>(byId.values());
    }

    @Override
    public Layer findById(Integer layerId) {
        return byId.get(layerId);
    }

    @Override
    public List<Layer> findByServiceId(int serviceId) {
        if (byService.containsKey(serviceId)) {
            return new ArrayList<>(byService.get(serviceId));
        }
        return new ArrayList<>();
    }

    @Override
    public List<Integer> findIdByServiceId(int serviceId) {
        List<Integer> results = new ArrayList<>();
        if (byService.containsKey(serviceId)) {
            for (Layer l : byService.get(serviceId)) {
                results.add(l.getId());
            }
        }
        return results;
    }

    @Override
    public List<QName> findNameByServiceId(int serviceId) {
        if (byServiceName.containsKey(serviceId)) {
            return new ArrayList<>(byServiceName.get(serviceId));
        }
        return new ArrayList<>();
    }

    @Override
    public List<Integer> findByDataId(int dataId) {
        List<Integer> ids = new ArrayList<>();
        if (byData.containsKey(dataId)) {
            for (Layer l : byData.get(dataId)) {
                ids.add(l.getId());
            }
        }
        return ids;
    }

     @Override
    public Layer findByServiceIdAndLayerName(int serviceId, String layerName) {
        return byServiceIdLayerName.get(serviceId + '-' + layerName);
    }

    @Override
    public Integer findIdByServiceIdAndLayerName(int serviceId, String layerName) {
        if (byServiceIdLayerName.containsKey(serviceId + '-' + layerName)) {
            return byServiceIdLayerName.get(serviceId + '-' + layerName).getId();
        }
        return null;
    }

    @Override
    public Layer findByServiceIdAndLayerName(int serviceId, String layerName, String namespace) {
        return byServiceIdLayerNameNmsp.get(serviceId + '-' + layerName + '-' + namespace);
    }

    @Override
    public Layer findByServiceIdAndAlias(int serviceId, String alias) {
        return byServiceIdAlias.get(serviceId + '-' + alias);
    }

    @Override
    public Layer findByServiceIdAndDataId(int serviceId, int dataId) {
        return byServiceIdDataId.get(serviceId + "-" + dataId);
    }

    @Override
    public List<Layer> getLayersByLinkedStyle(int styleId) {
        List<Layer> results = new ArrayList<>();
        for (Layer l : byId.values()) {
            for (Style s : styleRepository.findByLayer(l.getId())) {
                if (s.getId().equals(styleId)) {
                    results.add(l);
                    break;
                }
            }
        }
        return results;
     }

    @Override
    public List<Layer> getLayersRefsByLinkedStyle(int styleId) {
        return getLayersByLinkedStyle(styleId);
     }

    ////--------------------------------------------------------------------///
    ////------------------------    TRANSACTIONAL  -------------------------///
    ////--------------------------------------------------------------------///

    @Override
    public Integer create(Layer layer) {
        if (layer != null) {
            layer.setId(currentId);

            Path layerDir = getDirectory(LAYER_DIR);
            Path layerFile = layerDir.resolve(currentId + ".xml");
            writeObjectInPath(layer, layerFile, pool);

            byId.put(layer.getId(), layer);
            if (!byData.containsKey(layer.getDataId())) {
                List<Layer> layers = new ArrayList<>();
                layers.add(layer);
                byData.put(layer.getDataId(), layers);
            } else {
                byData.get(layer.getDataId()).add(layer);
            }

            if (!byService.containsKey(layer.getService())) {
                List<Layer> layers = new ArrayList<>();
                layers.add(layer);
                byService.put(layer.getService(), layers);
            } else {
                byService.get(layer.getService()).add(layer);
            }

            if (!byServiceName.containsKey(layer.getService())) {
                List<QName> layers = new ArrayList<>();
                layers.add(new QName(layer.getNamespace(), layer.getName()));
                byServiceName.put(layer.getService(), layers);
            } else {
                byServiceName.get(layer.getService()).add(new QName(layer.getNamespace(), layer.getName()));
            }

            String serviceIdLayerName = layer.getService() + '-' + layer.getName();
            byServiceIdLayerName.put(serviceIdLayerName, layer);

            String serviceIdLayerNameNmsp = layer.getService() + '-' + layer.getName() + '-' + layer.getNamespace();
            byServiceIdLayerNameNmsp.put(serviceIdLayerNameNmsp, layer);

            String serviceIdAlias = layer.getService() + '-' + layer.getAlias();
            byServiceIdAlias.put(serviceIdAlias, layer);

            String serviceIdDataId = layer.getService() + "-" + layer.getDataId();
            byServiceIdDataId.put(serviceIdDataId, layer);

            currentId++;
            return layer.getId();
        }
        return null;
    }

    @Override
    public void update(Layer layer) {
        if (byId.containsKey(layer.getId())) {

            delete(layer.getId());

            Path layerDir = getDirectory(LAYER_DIR);
            Path layerFile = layerDir.resolve(layer.getId() + ".xml");
            writeObjectInPath(layer, layerFile, pool);

            byId.put(layer.getId(), layer);
            if (!byData.containsKey(layer.getDataId())) {
                List<Layer> layers = new ArrayList<>();
                layers.add(layer);
                byData.put(layer.getDataId(), layers);
            } else {
                byData.get(layer.getDataId()).add(layer);
            }

            if (!byService.containsKey(layer.getService())) {
                List<Layer> layers = new ArrayList<>();
                layers.add(layer);
                byService.put(layer.getService(), layers);
            } else {
                byService.get(layer.getService()).add(layer);
            }

            if (!byServiceName.containsKey(layer.getService())) {
                List<QName> layers = new ArrayList<>();
                layers.add(new QName(layer.getNamespace(), layer.getName()));
                byServiceName.put(layer.getService(), layers);
            } else {
                byServiceName.get(layer.getService()).add(new QName(layer.getNamespace(), layer.getName()));
            }

            String serviceIdLayerName = layer.getService() + '-' + layer.getName();
            byServiceIdLayerName.put(serviceIdLayerName, layer);

            String serviceIdLayerNameNmsp = layer.getService() + '-' + layer.getName() + '-' + layer.getNamespace();
            byServiceIdLayerNameNmsp.put(serviceIdLayerNameNmsp, layer);

            String serviceIdAlias = layer.getService() + '-' + layer.getAlias();
            byServiceIdAlias.put(serviceIdAlias, layer);

            String serviceIdDataId = layer.getService() + "-" + layer.getDataId();
            byServiceIdDataId.put(serviceIdDataId, layer);
        }
    }

    @Override
    public void delete(int id) {
        if (byId.containsKey(id)) {

            Layer layer = byId.get(id);

            Path layerDir = getDirectory(LAYER_DIR);
            Path layerFile = layerDir.resolve(layer.getId() + ".xml");
            try {
                Files.delete(layerFile);
            } catch (IOException ex) {
                throw new ConstellationPersistenceException(ex);
            }

            byId.remove(layer.getId());
            if (byData.containsKey(layer.getDataId())) {
                byData.get(layer.getDataId()).remove(layer);
            }
            if (byService.containsKey(layer.getService())) {
                byService.get(layer.getService()).remove(layer);
            }
            if (byServiceName.containsKey(layer.getService())) {
                byServiceName.get(layer.getService()).remove(new QName(layer.getNamespace(), layer.getName()));
            }

            String serviceIdLayerName = layer.getService() + '-' + layer.getName();
            byServiceIdLayerName.remove(serviceIdLayerName);

            String serviceIdLayerNameNmsp = layer.getService() + '-' + layer.getName() + '-' + layer.getNamespace();
            byServiceIdLayerNameNmsp.remove(serviceIdLayerNameNmsp);

            String serviceIdAlias = layer.getService() + '-' + layer.getAlias();
            byServiceIdAlias.remove(serviceIdAlias);

            String serviceIdDataId = layer.getService() + "-" + layer.getDataId();
            byServiceIdDataId.remove(serviceIdDataId);

            for (Style s : styleRepository.findByLayer(id)) {
                styleRepository.unlinkStyleToLayer(s.getId(), id);
            }
        }
    }

    @Override
    public void updateLayerTitle(int layerID, String newTitle) {
        if (byId.containsKey(layerID)) {
            Layer layer = byId.get(layerID);
            layer.setTitle(newTitle);
            update(layer);
        }
    }

    @Override
    public int deleteServiceLayer(Integer service) {
        int i = 0;
        if (byService.containsKey(service)) {
            for (Layer l : byService.get(service)) {
                delete(l.getId());
                i++;
            }
        }
        return i;
    }

}
