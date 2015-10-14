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

    instance.report_birt.FieldMultiSelect = instance.web.form.FieldSelection.extend({
        template: 'FieldMultiSelect',
        widget_class: "oe_form_field_multiselect", store_dom_value: function () {
            if (!this.get('effective_readonly')) {
                var cur_value = this.get_value(),
                    new_value = this.$('select').val();

                var changed = _.find(new_value, function(e){
                    return !_.contains(cur_value, e);
                });
                if(!!changed){
                    this.internal_set_value(new_value);
                }
            }
        },
        set_value: function(value_) {
            value_ = value_ === null ? false : value_;
            this.set({'value': value_});
        },
        store_dom_value: function () {
            var self = this;

            if (!this.get('effective_readonly') && this.$('option:selected').length) {
                var cur_value = this.get_value(),
                new_value = this.$('select option').map(function(i){
                    if($(this).is(':selected') == true) return self.values[i][0];}
                ).toArray();

                // check for additions and removal
                var changed = _.intersection(new_value, cur_value).length != Math.max(new_value.length, cur_value.length);
                if(changed){
                    this.internal_set_value(new_value);
                }
            }
        },
        render_value: function() {
            var self = this,
                cur_value = this.get_value();
            if (!this.get("effective_readonly")) {
                this.$('option').each(function(i, e){
                    // NOTE: cur_value is an array of ids and this.values is an array of options.
                    // '*' which means select all (not a BIRT construct. BIRT supports only constant default values).
                    if(cur_value[0] == '*' || _.contains(cur_value, self.values[i][0])){
                        $(this).attr('selected', 'selected');
                    }
                });
            } else {
                var ul = $('<ul>'), li;
                _(cur_value).each(function(e){
                    li = $('<ol>');
                    li.text(e);
                    ul.append(li);
                });
                this.$el.empty().append(ul);
            }
        }
    });
    instance.web.form.widgets.add('multiselect', 'instance.report_birt.FieldMultiSelect');
};
