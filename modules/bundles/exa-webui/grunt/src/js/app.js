/*
 * Constellation - An open source and standard compliant SDI
 *      http://www.constellation-sdi.org
 *   (C) 2014, Geomatys
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details..
 */


/* App Module */
/*jshint -W079 */
var cstlAdminApp = angular.module('CstlAdminApp', ['CstlAdminDep']);


cstlAdminApp
    .config(['$routeProvider', '$httpProvider', '$translateProvider', '$translatePartialLoaderProvider',
        '$keepaliveProvider','$idleProvider', 'PermissionResolver',
        function ($routeProvider, $httpProvider, $translateProvider, $translatePartialLoaderProvider,
                  $keepaliveProvider, $idleProvider, PermissionResolver) {

            $httpProvider.interceptors.push('AuthInterceptor');

            $routeProvider
                .when('/admin', {
                    template: '<admin-manager></admin-manager>',
                    resolve: angular.extend({},PermissionResolver.factory('admin'))
                })
                .when('/password', {
                    templateUrl: 'views/password.html',
                    controller: 'PasswordController'
                })
                .when('/logout', {
                    templateUrl: 'views/main.html',
                    controller: 'LogoutController'
                })
                .when('/webservice', {
                    templateUrl: 'views/webservice/webservice.html',
                    controller: 'WebServiceController',
                    controllerAs: 'wsCtrl',
                    resolve: angular.extend({},PermissionResolver.factory('publish'))
                })
                .when('/webservice/:type/:id', {
                    templateUrl: 'views/webservice/edit.html',
                    controller: 'WebServiceEditController',
                    resolve: angular.extend({},PermissionResolver.factory('publish'))
                })
                .when('/webservice/:type/:id/source', {
                    templateUrl: 'views/webservice/source.html',
                    controller: 'WebServiceChooseSourceController',
                    controllerAs: 'wsChooseCtrl',
                    resolve: angular.extend({},PermissionResolver.factory('publish'))
                })
                .when('/webservice/:type', {
                    templateUrl: 'views/webservice/create.html',
                    controller: 'WebServiceCreateController',
                    controllerAs: 'wsCreateCtrl',
                    resolve: angular.extend({},PermissionResolver.factory('publish'))
                })
                .when('/data', {
                    templateUrl: 'views/data/data.html',
                    controller: 'DatasetDashboardController',
                    controllerAs: 'dc',
                    resolve: angular.extend({},PermissionResolver.factory('data'))
                })
                .when('/metadata', {
                    templateUrl: 'views/metadata/metadata.html',
                    controller: 'MetadataDashboardController',
                    controllerAs: 'mdCtrl',
                    resolve: angular.extend({},PermissionResolver.factory('data'))
                })
                .when('/thesaurus', {
                    templateUrl: 'views/thesaurus/thesaurus.html',
                    controller: 'ThesaurusDashboardController',
                    controllerAs: 'tdc',
                    resolve: angular.extend({},PermissionResolver.factory('data'))
                })
                .when('/sensors/:id?', {
                    templateUrl: 'views/sensor/sensors.html',
                    controller: 'SensorsController',
                    resolve: angular.extend({},PermissionResolver.factory('data'))
                })
                .when('/styles', {
                    templateUrl: 'views/style/styles.html',
                    controller: 'StylesController',
                    controllerAs: 'stlCtrl',
                    resolve: angular.extend({},PermissionResolver.factory('data'))
                })
                .when('/mapcontext', {
                    templateUrl: 'views/mapcontext/mapcontext.html',
                    controller: 'MapcontextController',
                    resolve: angular.extend({},PermissionResolver.factory('data'))
                })
                .when('/tasks', {
                    templateUrl: 'views/tasks/tasks.html',
                    controller: 'TasksController',
                    resolve: angular.extend({},PermissionResolver.factory('data'))
                })
                .when('/profile', {
                    templateUrl: 'views/profile.html',
                    controller: 'UserAccountController',
                    resolve: {
                        'user': function(Examind){
                            return Examind.users.getCurrentUser();
                        },
                        'roles': function(Examind){
                            return Examind.roles.getRoles();
                        }
                    }
                })
                .when('/disclaimer', {
                    templateUrl: 'views/disclaimer.html',
                    controller: 'MainController'
                })
                .when('/help', {
                    templateUrl: 'views/help.html',
                    controller: 'MainController'
                })
                .when('/add_data_workflow', {
                    reloadOnSearch: false,
                    template: '<wizard-data-import></wizard-data-import>'
                })
                .otherwise({
                    templateUrl: 'views/main.html',
                    controller: 'MainController'
                });

            // Initialize angular-translate
            $translateProvider.useLoader('$translatePartialLoader', {
                urlTemplate: 'i18n/{lang}/{part}.json'
            });
            $translatePartialLoaderProvider.addPart('ui-menu');
            $translatePartialLoaderProvider.addPart('ui');
            $translatePartialLoaderProvider.addPart('metadata');
            $translatePartialLoaderProvider.addPart('formats');

            $translateProvider.preferredLanguage('en');

            // remember language
            $translateProvider.useCookieStorage();


            var tokenDuration = 30*60; //default is 30min halftime
            //The idle timeout duration in seconds.
            // After this amount of time passes without the user performing an action
            // that triggers one of the watched DOM events, the user is considered idle.
            if($.cookie('token_life')){
            	tokenDuration = $.cookie('token_life')/2;
            }else{
            	var cookieToken = $.cookie('access_token');
            	if(cookieToken && cookieToken.indexOf('_') !==-1){
                    var splitArr = cookieToken.split('_');
                    tokenDuration = splitArr[splitArr.length-1]/1000;
            	}
            }
            if (Number.isNaN(tokenDuration)) {
                tokenDuration = 30*60;
            }
            console.log("Token duration set to: " + tokenDuration);

            $idleProvider.idleDuration(tokenDuration*2);

            //The warning duration in seconds.
            // Once a user becomes idle, the warning countdown starts at this value
            // and ticks down until it reaches 0, after which the user is considered timed out.
            $idleProvider.warningDuration(60); //1min for warning

            //Must be greater than 0 seconds.
            // This specifies how often the keepalive event
            // is triggered and the HTTP request is issued.
            $keepaliveProvider.interval(tokenDuration);

        }])
        .run(['$rootScope', '$location', 'Examind', 'StompService','$idle', 'Permission', '$translate',
            function($rootScope, $location, Examind, StompService, $idle, Permission, $translate) {

            $rootScope.authenticated=true;

            $rootScope.access_token = Examind.authentication.getToken();

            // Call when the 401 response is returned by the client
            $rootScope.$on('event:auth-loginRequired', function() {
                //Examind.authentication.logout();
                window.location.href="index.html";
            });

            $rootScope.hasRole = function(){return false;};
            $rootScope.hasPermission = function(){return false;};

            // call on window is about to unload its resources (refresh page or close tab)
            window.addEventListener("beforeunload", function( event ) {

                //disconnect form websocket to avoid BrokenPipe error on server
                StompService.disconnect();
            });

            //starts watching for idleness, or resets the idle/warning state and continues watching.
            $idle.watch();

            // set language from user locale preferences
            Permission.promise.then(function(){
                var account= Permission.getAccount();
                $translate.use(account.locale ? account.locale :'en');
            });

        }]);


function applyScrollValues(scrollPos,topHeight,blockHeight,blockInfo,limit) {
    if (scrollPos > topHeight && blockHeight < limit ) {
        if((scrollPos+blockHeight) <= (limit+topHeight)) {
            blockInfo.css('padding-top', scrollPos - topHeight);
        }
    } else {
        blockInfo.css('padding-top', 0);
    }
}

$(window).scroll(function(){
    var limit,scrollPos,topHeight;
    var blockInfo = $('#block-information-right');
    var blockHeight = blockInfo.height();
    if(window.location.hash.indexOf('/data') !== -1){
        limit = $('#contentList').height();
        scrollPos = $(this).scrollTop();
        var delta = 240;
        if($('#navtabsData').get(0)){
            delta = 275;
        }
        topHeight = $('#advancedSearchPanel').height() + delta;
        applyScrollValues(scrollPos,topHeight,blockHeight,blockInfo,limit);
    } else if(window.location.hash.indexOf('/metadata')!==-1){
        limit = $('#contentList').height();
        scrollPos = $(this).scrollTop();
        topHeight = $('#advancedSearchPanel').height() + 275;
        applyScrollValues(scrollPos,topHeight,blockHeight,blockInfo,limit);
    } else if(window.location.hash.indexOf('/styles') !== -1 || window.location.hash.indexOf('/mapcontext') !== -1){
        limit = $('#contentList').height();
        scrollPos = $(this).scrollTop();
        topHeight = 245;
        applyScrollValues(scrollPos,topHeight,blockHeight,blockInfo,limit);
    } else if(window.location.hash.indexOf('/sensors')!==-1){
        limit = $('#contentList').height();
        scrollPos = $(this).scrollTop();
        topHeight = 240;
        applyScrollValues(scrollPos,topHeight,blockHeight,blockInfo,limit);
    } else if(window.location.hash.indexOf('/tasks') !==-1){
        limit = $('#contentList').height();
        scrollPos = $(this).scrollTop();
        topHeight = 200;
        applyScrollValues(scrollPos,topHeight,blockHeight,blockInfo,limit);
    } else{
        limit = $('#contentList').height();
        scrollPos = $(this).scrollTop();
        topHeight = 255;
        applyScrollValues(scrollPos,topHeight,blockHeight,blockInfo,limit);
    }

});
