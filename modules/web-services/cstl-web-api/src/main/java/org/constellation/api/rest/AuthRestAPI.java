/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2017 Geomatys.
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
package org.constellation.api.rest;

import java.security.Principal;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.LocaleUtils;
import org.constellation.business.IMailBusiness;
import org.constellation.dto.TokenTransfer;
import org.constellation.dto.UserWithRole;
import org.constellation.dto.CstlUser;
import org.constellation.dto.AcknowlegementType;
import org.constellation.dto.user.ForgotPassword;
import org.constellation.dto.user.Login;
import org.constellation.dto.user.ResetPassword;
import org.constellation.engine.security.AuthenticationProxy;
import org.constellation.security.SecurityManagerHolder;
import org.constellation.security.UnknownAccountException;
import org.constellation.token.TokenUtils;
import org.geotoolkit.util.StringUtilities;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.web.bind.annotation.RequestMethod.*;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

/**
 *
 * @author Johann Sorel (Geomatys)
 */
@RestController
public class AuthRestAPI extends AbstractRestAPI{

    @Inject
    private IMailBusiness mailService;

    @Inject
    @Qualifier("authenticationProxy")
    private AuthenticationProxy authProxy;

    /**
     * Authenticates a user and creates an authentication token.
     *
     * @param login pojo that contains the name and the password of the user.
     * @return A transfer containing the authentication token.
     */
    @RequestMapping(value="/auth/login", method=POST, produces=APPLICATION_JSON_VALUE, consumes=APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenTransfer> login(@RequestBody Login login, HttpServletResponse response) {
        try {
            final String createToken = authProxy.performLogin(login.getUsername(), login.getPassword(), response);

            // here we set some legacy stuff :
            // 1) the token should not been return has it should be set in a cookie int the response
            // 2) the user id shouls not be return either. we are supposed to know who we are by requesting our account with the token (and it seems unused by the JS)
            return new ResponseEntity<>(new TokenTransfer(createToken, -1), HttpStatus.OK);

        } catch (BadCredentialsException ex) {
            return new ResponseEntity<>(UNAUTHORIZED);
        } catch (DisabledException ex) {
            return new ResponseEntity<>(FORBIDDEN);
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }
    }

    @RequestMapping(value="/auth/extendToken", method=GET, produces=TEXT_PLAIN_VALUE)
    public ResponseEntity<String> extendToken(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        try {
            final String token = authProxy.extendToken(httpServletRequest, httpServletResponse);

            // here we set some legacy stuff :
            // 1) the token should not been return has it should be set in a cookie int the response
            // 2) i don't know why but if i return the string in the entity, i got a 406 exception (tested in Spring boot).
            IOUtils.write(token, httpServletResponse.getOutputStream());

            return new ResponseEntity(HttpStatus.OK);
        } catch (UnknownAccountException ex) {
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        } catch(Throwable ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }
    }

    @RequestMapping(value="/auth/forgotPassword", method=POST, produces=APPLICATION_JSON_VALUE, consumes=APPLICATION_JSON_VALUE)
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity forgotPassword(final HttpServletRequest request, @RequestBody final ForgotPassword forgotPassword) {

        try {
            final String email = forgotPassword.getEmail();
            final String uuid = DigestUtils.sha256Hex(email + System.currentTimeMillis());
            final Optional<CstlUser> userOptional = userBusiness.findByEmail(email);

            if (!userOptional.isPresent()) return new ErrorMessage(NOT_FOUND).message("User not found").i18N(I18nCodes.User.NOT_FOUND).build();

            final CstlUser user = userOptional.get();
            user.setForgotPasswordUuid(uuid);
            userBusiness.update(user);

            final String baseUrl = "http://" + request.getHeader("host") + request.getContextPath();
            final String resetPasswordUrl = baseUrl + "/reset-password.html?uuid=" + uuid;

            final ResourceBundle bundle = ResourceBundle.getBundle("org/constellation/admin/mail/mail", LocaleUtils.toLocale(user.getLocale()));
            final Object[] args = {user.getFirstname(), user.getLastname(), resetPasswordUrl};

            mailService.send(bundle.getString("account.password.reset.subject"),
                MessageFormat.format(bundle.getString("account.password.reset.body"), args),
                Collections.singletonList(email));
            return new ResponseEntity(OK);

        } catch(Throwable ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }
    }

    @RequestMapping(value="/auth/resetPassword", method=POST, produces=APPLICATION_JSON_VALUE, consumes=APPLICATION_JSON_VALUE)
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity resetPassword(@RequestBody final ResetPassword resetPassword) {

        final String newPassword = resetPassword.getPassword();
        final String uuid = resetPassword.getUuid();

        if(newPassword != null && uuid != null && !newPassword.isEmpty() && !uuid.isEmpty()){
            final Optional<CstlUser> userOptional = userBusiness.findByForgotPasswordUuid(uuid);

            if (!userOptional.isPresent()) return new ErrorMessage(NOT_FOUND).message("User not found").i18N(I18nCodes.User.NOT_FOUND).build();

            final CstlUser cstlUser = userOptional.get();
            cstlUser.setPassword(StringUtilities.MD5encode(newPassword));
            cstlUser.setForgotPasswordUuid(null);
            userBusiness.update(cstlUser);
            return new ResponseEntity(HttpStatus.OK);
        }

        return new ResponseEntity(HttpStatus.BAD_REQUEST);
    }

    @RequestMapping(value="/auth/logout", method=DELETE)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authProxy.performLogout(request, response);
        final HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @RequestMapping(value="/auth/account", method=GET, produces=APPLICATION_JSON_VALUE)
    public ResponseEntity account(HttpServletRequest req) {
        Principal userPrincipal = req.getUserPrincipal();
        String username = null;
        if (userPrincipal != null) {
            username = userPrincipal.getName();
        } else {
            final String token = TokenUtils.extractAccessToken(req);
            if (token != null) {
                username = TokenUtils.getUserNameFromToken(token);
            } else {
                LOGGER.log(Level.WARNING,"No token in request");
            }
        }
        if (username == null || username.isEmpty()) {
            return new ResponseEntity(UNAUTHORIZED);
        }

        final Optional<UserWithRole> role = userBusiness.findOneWithRole(username);
        if (role.isPresent()) {
            role.get().setPassword("*******");
            return new ResponseEntity(role.get(),OK);
        } else {
            return new ResponseEntity(NOT_FOUND);
        }
    }

    /**
     * @return a {@link ResponseEntity} which contains requester user name
     */
    @RequestMapping(value="/auth/current", method=GET, produces=APPLICATION_JSON_VALUE)
    public ResponseEntity current() {
        final Optional<UserWithRole> opt = userBusiness.findOneWithRole(SecurityManagerHolder.getInstance().getCurrentUserLogin());
        if (opt.isPresent()) {
            return new ResponseEntity(opt.get(), HttpStatus.OK);
        } else {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value="/auth/ping", method=GET, produces=APPLICATION_JSON_VALUE)
    public ResponseEntity ping() {
        final AcknowlegementType response = new AcknowlegementType("Success",
                "You have access to the configuration service");
        return new ResponseEntity(response,HttpStatus.OK);
    }

}