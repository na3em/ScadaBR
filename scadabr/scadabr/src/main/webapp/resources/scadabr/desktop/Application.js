define([
    "dojo/_base/declare",
    'dojo/router',
    "scadabr/desktop/Header",
    "scadabr/desktop/MainView",
    "scadabr/desktop/Footer",
    "dijit/Toolbar",
    "dijit/ToolbarSeparator",
    "dijit/form/Button",
    "dijit/layout/LayoutContainer"
], function (
        declare,
        router,
        Header,
        MainView,
        Footer,
        Toolbar,
        ToolbarSeparator,
        Button,
        LayoutContainer
        ) {
    return declare("scadabr/desktop/Application", [LayoutContainer], {
        _header: null,
        _mainView: null,
        _footer: null,
        _toolbar: null,
        postCreate: function () {
            this.inherited(arguments);
            this._header = new Header({region: 'top'});
            this.addChild(this._header);
            this._toolbar = new Toolbar({region: 'top'});
            this.addChild(this._toolbar);
            this._mainView = new MainView({region: 'center'});
            this.addChild(this._mainView);
            this._footer = new Footer({region: 'bottom'});
            this.addChild(this._footer);
            var btn;
            var tbs;

            btn = new Button({label: 'Watchlist', iconClass: 'scadaBrWatchListIcon', showLabel: false, onClick: 'window.location = "#watchList";'});
            this._toolbar.addChild(btn);
            btn = new Button({label: 'Alarms', iconClass: 'scadaBrEventsIcon', showLabel: false, onClick: 'window.location = "#events";'});
            this._toolbar.addChild(btn);
            btn = new Button({label: 'Data sources', iconClass: 'dsIcon', showLabel: false, onClick: 'window.location = "#dataSources";'});
            this._toolbar.addChild(btn);
            btn = new Button({label: 'Point hierarchy', iconClass: 'scadaBrPointHierarchyIcon', showLabel: false, onClick: 'window.location = "#dataPoints";'});
            this._toolbar.addChild(btn);
            tbs = new ToolbarSeparator();
            this._toolbar.addChild(tbs);
            btn = new Button({label: 'Help', iconClass: 'scadaBrHelpIcon', showLabel: false, onClick: 'MainPage.showHelp("")'});
            this._toolbar.addChild(btn);
            tbs = new ToolbarSeparator();
            this._toolbar.addChild(tbs);
//Login
            // Will appear in reverse order... use ContentPane???
            btn = new Button({label: 'Logout', iconClass: 'scadaBrLogoutIcon', showLabel: false, onClick: 'window.location = "#logout";', style: 'float:right;'});
            this._toolbar.addChild(btn);

            btn = new Button({label: 'Make this my default page', disabled: true, iconClass: 'scadaBrSetHomeUrlIcon', showLabel: false, onClick: 'setHomeUrl();', style: 'float:right;'});
            this._toolbar.addChild(btn);
            btn = new Button({label: 'Go to my default page', disabled: true, iconClass: 'scadaBrGotoHomeUrlIcon', showLabel: false, onClick: 'window.location = "#homeUrl";', style: 'float:right;'});
            this._toolbar.addChild(btn);
            tbs = new ToolbarSeparator({style: 'float:right;'});
            this._toolbar.addChild(tbs);

            this.initRouter();
        },
        destroyMainView: function() {
                if (this._mainView) {
                    this.removeChild(this._mainView);
                    this._mainView.destroyRecursive(true);
                    this._mainView = null;
                }
            
        },
        createMainView: function(MainViewClass) {
                    this._mainView = new MainViewClass({region: 'center'});
                    this.addChild(this._mainView);
            
        },
        initRouter: function () {
            var self = this;
            router.register("dataSources", function (evt) {
                evt.preventDefault();
                self.destroyMainView();
                require(["scadabr/desktop/DataSourcesView"], function(DataSourcesView) {
                    self.createMainView(DataSourcesView)
                });
            });

            router.register("watchLists", function (evt) {
                evt.preventDefault();
                self.destroyMainView();
                require(["scadabr/desktop/WatchListsView"], function (WatchListsView) {
                    self.createMainView(WatchListsView)
                });
            });

            router.register("events", function (evt) {
                evt.preventDefault();
                self.destroyMainView();
                require(["scadabr/desktop/EventsView"], function (EventsView) {
                    self.createMainView(EventsView)
                });
            });

            router.register("dataPoints", function (evt) {
                evt.preventDefault();
                self.destroyMainView();
                require(["scadabr/desktop/DataPointsView"], function (DataPointsView) {
                    self.createMainView(DataPointsView)
                });
            });

            router.startup();
            if (window.location.hash) {
                router.go(window.location.hash);
            } else {
                router.go("");
            }
        }
    });
});
