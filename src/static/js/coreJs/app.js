Ext.onReady(function(){
    Ext.application({
        name: 'rule',

        launch: function () {
            Ext.Msg.alert(this.name, 'Ready to go!');
        }
    });
});