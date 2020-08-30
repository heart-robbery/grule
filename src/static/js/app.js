Ext.onReady(function(){
    Ext.on('e1', function () {
        console.log('fired e1 xxxxxxxxxxxx')
    });
    Ext.application({
        name: 'sys',
        launch: function () {
            Ext.create('Ext.container.Viewport', {
                layout: 'border',
                items: [{
                    region: 'west', collapsible: true, title: '菜单导航',titleAlign: 'center',
                    width: 200, split: true,
                    layout: 'fit',
                    items: [{
                        xtype: 'treepanel',
                        highlightPath: true,
                        singleExpand: true,
                        rootVisible: false,
                        selectionchange: function(me, record, eOpts) {
                            console.log(me);
                            console.log(record);
                            console.log(eOpts);
                        },
                        store: {
                            root: {
                                expanded: true,
                                children: [{
                                    text: '配置中心',
                                    iconCls: 'x-fa fa-folder',
                                    children: [{
                                        text: '规则记录',
                                        leaf: true,
                                        iconCls: 'x-fa fa-book'
                                    }, {
                                        text: '策略记录',
                                        leaf: true,
                                        iconCls: 'x-fa fa-graduation-cap'
                                    }]
                                }, {
                                    text: '数据中心',
                                    expanded: false,
                                    iconCls: 'x-fa fa-folder',
                                    children: [{
                                        text: '历史决策',
                                        leaf: true,
                                    }, {
                                        text: '接口记录',
                                        leaf: true,
                                    }]
                                }, {
                                    text: '分析中心',
                                    children: [{
                                        leaf: true,
                                        text: '拒绝率'
                                    }]
                                }]
                            }
                        }
                    }]
                    // could use a TreePanel or AccordionLayout for navigational items
                }, {
                    region: 'east', title: 'East Panel', collapsible: true, collapsed: true,
                    split: true, width: 150
                }, {
                    region: 'center',
                    xtype: 'tabpanel', // TabPanel itself has no title
                    activeTab: 0,      // First tab active by default
                    items: {
                        title: 'Default Tab',
                        html: 'The first tab\'s content. Others may be added dynamically'
                    }
                }]
            });
        }
    });
});