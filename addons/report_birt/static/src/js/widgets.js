openerp.report_birt = function(instance) {
    var QWeb = instance.web.qweb,
        _t   = instance.web._t,
        _lt  = instance.web._lt;

    instance.report_birt.TimeWidget = instance.web.DateTimeWidget.extend({
        jqueryui_object: "timepicker",
        type_of_date: "time",
        picker: function() {
            if(arguments[0] === 'setDate' && _.isEmpty(this.get('value'))){
                // NOTE: this is a hack - default time (now) is almost always useless.
                var args = Array.prototype.slice.call(arguments),
                    time = args[1];
                time.setHours(0);
                time.setMinutes(0);
                time.setSeconds(0);
                return $.fn[this.jqueryui_object].apply(this.$input_picker, args);
            }
            return $.fn[this.jqueryui_object].apply(this.$input_picker, arguments);
        },
        on_picker_select: function(text, instance_) {
            // NOTE: as of jQuery timepicker v0.9.9, retriving time from timepicker with getDate always returns jQuery object
            var time_str = this.picker('getDate').val(), // this.picker is a timepicker
                val = instance.web.str_to_time(time_str + ":00");  // seconds not returned

            this.$input
                .val(val ? this.format_client(val) : '')
                .change()
                .focus();
        },
        parse_client: function(v) {
            return instance.web.parse_value(v, {"widget": 'time'});
        },
        format_client: function(v) {
            return instance.web.format_value(v, {"widget": 'time'});
        }
    });

    instance.report_birt.FieldTimePicker = instance.web.form.FieldDate.extend({
        widget_class: "oe_form_field_date",
        build_widget: function() {
            return new instance.report_birt.TimeWidget(this);
        },
        render_value: function() {
            if (!this.get("effective_readonly")) {
                this.datewidget.set_value(this.get('value'));
            } else {
                this.$el.text(instance.web.format_value(this.get('value'), {'widget': 'time'}, ''));
            }
        }
    });
    instance.web.form.widgets.add('timepicker', 'instance.report_birt.FieldTimePicker');

    instance.report_birt.TimeColumn = instance.web.list.Column.extend({
        _format: function(row_data, options){
            var value = row_data[this.id].value;
            if(/^\d{2}:\d{2}$/.test(value)) value += ":00";

            return _.escape(
                instance.web.format_value(value, {'widget': 'time'}),
                this,
                options.value_if_empty);
        }
    });
    instance.web.list.columns.add('field.time', 'instance.report_birt.TimeColumn');
};
