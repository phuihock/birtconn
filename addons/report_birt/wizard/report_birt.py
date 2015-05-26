# -*- encoding: utf-8 -*-

from collections import OrderedDict
from datetime import datetime, date, time
from lxml import etree
from openerp import netsvc, tools
from openerp.osv import fields, osv
from openerp.report.interface import report_int
import os
import re
import requests
import simplejson as json


def serialize(obj):
    if isinstance(obj, datetime):
        return datetime.strftime(obj, tools.DEFAULT_SERVER_DATETIME_FORMAT)
    if isinstance(obj, date):
        return datetime.strftime(obj, tools.DEFAULT_SERVER_DATE_FORMAT)
    if isinstance(obj, time):
        return datetime.strftime(obj, tools.DEFAULT_SERVER_TIME_FORMAT)
    raise TypeError(obj)


class report_birt_report_wizard(osv.osv_memory):
    _name = 'report_birt.report_wizard'
    _description = 'Birt Report Wizard'

    _columns = {
        'report_name': fields.char(size=64, string="Report Name"),
        'values': fields.text(string="Values"),
    }

    def _report_get(self, cr, uid, context=None):
        if 'report_name' in context:
            report_obj = self.pool.get('ir.actions.report.xml')
            found = report_obj.search(cr, uid, [('report_name', '=', context['report_name'])])
            if found:
                report_id = found[0]
                report = report_obj.read(cr, uid, report_id, ['report_file'])

                api = tools.config.get_misc('birt-rs', 'api')
                report_api = os.path.join(api, 'report')
                r = requests.get('%s?report_file=%s' % (report_api, report['report_file']))
                return r.json()
        return {}

    def default_get(self, cr, uid, fields_list, context=None):
        res = {}
        parameters = self._report_get(cr, uid, context)
        for param in parameters:
            name = param['name']
            if param['type'] == 'scalar':
                fieldType = param['fieldType']
                if fieldType == 'time':
                    # time is actually not a field, and is not supported by OpenERP by default.
                    # is a type of parameter that is directly supported by birt reporting.
                    # we treat time as datetime field, but set to use timepicker widget instead.
                    fieldType = 'datetime'

                if fieldType in ['boolean']:
                    val = param['defaultValue']
                else:
                    val = getattr(fields, fieldType)(string=param['promptText'])._symbol_set[1](param['defaultValue'])
                res[name] = val
        return res


    def fields_get_meta(self, cr, uid, param, context, res, fgroup=None):
        name = param['name']
        meta = {}
        if param['type'] == 'group':
            for p in param['parameters']:
                self.fields_get_meta(cr, uid, p, context, res, {'name': param['name'], 'string': param['promptText']})

        if param['type'] == 'scalar':
            # refer to field_to_dict if you don't understand why
            if 'selection' in param and param['selection']:
                # NOTE: key must be a string, or else the dropdown box will not
                # display the choices correctly.
                # however, this will transform non-string value such as boolean,
                # integer into string, which must be taken care of when sending
                # the values to report server.
                meta['selection'] = [(str(x), y) for (x, y) in param['selection']]
                meta['type'] = 'selection'  # visual type
            else:
                meta['type'] = param['fieldType']

            meta['string'] = param['promptText']
            meta['required'] = param['required']
            meta['help'] = param['helpText']
            meta['context'] = {'type': param['fieldType']}  # actual data type

            if fgroup:
                meta['context']['fgroup'] = fgroup
            res[name] = meta


    def fields_get(self, cr, uid, fields_list=None, context=None, write_access=True):
        context = context or {}

        res = super(report_birt_report_wizard, self).fields_get(cr, uid, fields_list, context, write_access)
        for f in self._columns:
            for attr in ['invisible', 'readonly']:
                res[f][attr] = True

        parameters = self._report_get(cr, uid, context)
        for param in parameters:
            self.fields_get_meta(cr, uid, param, context, res)

        def order_by(parameters):
            def _wrap(name):
                for i, param in enumerate(parameters):
                    if param['type'] == 'group':
                        j = order_by(param['parameters'])(name)
                        if j != -1:
                            return (i * 10) + j
                    elif param['type'] == 'scalar' and param['name'] == name:
                            return (i * 10)
                return -1
            return _wrap

        if parameters:
            res_cur = res
            res_new = OrderedDict()

            # What we are trying to achieve here is to order fields by the order they (parameters) are defined in the rptdesign. Because
            # a parameter may be a group parameter, we will also inspect the group by loop check its content.
            for k in sorted(res, cmp, key=order_by(parameters)):
                res_new[k] = res_cur[k]
            res = res_new

        # if no fields is specified, return all
        if not fields_list:
            return res
        else:
            # return only what is requested
            return dict(filter(lambda (k, v): k in fields_list, a.items()))


    def create(self, cr, uid, vals, context=None):
        # 'vals' contains all field/value pairs, including report_name, parameters and values.
        # But we don't have all the columns since they are dynamically generated
        # based on report's parameters.
        values = dict(filter(lambda (x, y): x not in ['report_name', 'values'], vals.items()))
        values = json.dumps(values)

        report_name = context['report_name']
        vals = {
            'report_name': report_name,
            'values': values,
        }
        return super(report_birt_report_wizard, self).create(cr, uid, vals, context)

    def _get_lang_dict(self, cr, uid, context):
        lang_obj = self.pool.get('res.lang')
        lang = context and context.get('lang', 'en_US') or 'en_US'
        lang_ids = lang_obj.search(cr, uid, [('code','=',lang)])
        if not lang_ids:
            lang_ids = lang_obj.search(cr, uid, [('code','=','en_US')])
        lang_obj = lang_obj.browse(cr, uid, lang_ids[0])
        return {'date_format': lang_obj.date_format, 'time_format': lang_obj.time_format}

    def print_report(self, cr, uid, ids, context=None):
        lang_dict = self._get_lang_dict(cr, uid, context)
        values = self.read(cr, uid, ids[0], ['values'], context=context)['values']
        values = json.loads(values)

        report_name = context['report_name']
        fg = self.fields_get(cr, uid, context={'report_name': report_name})
        for (name, descriptor) in fg.items():
            if name not in self._columns:
                t1 = descriptor['context']['type']
                v1 = values[name]

                if t1 == 'boolean':
                    # this is how OpenERP field represents boolean value
                    v2 = v1 == 'True'
                elif t1 == 'time':
                    # NOTE: time is represented as datetime field with custom timepicker widget
                    time_format = lang_dict['time_format']
                    cc = time_format.count(':') + 1
                    v2 = datetime.strptime(':'.join(v1.split(':')[:cc]), time_format)
                    v2 = getattr(fields, 'datetime')(**descriptor)._symbol_set[1](v1)
                else:
                    v2 = getattr(fields, t1)(**descriptor)._symbol_set[1](v1)

                values[name] = v2

        return {
            'type': 'ir.actions.report.xml',
            'report_name': context['report_name'],
            'datas': values,
        }

    def fields_view_get(self, cr, uid, view_id=None, view_type='form', context=None, toolbar=False, submenu=False):
        res = super(report_birt_report_wizard, self).fields_view_get(cr, uid, view_id, view_type, context, toolbar, submenu)
        if view_type == 'search':
            return res

        xarch = etree.XML(res['arch'])
        group_values = xarch.xpath('//group[@name="values"]')[0]

        for field, descriptor in self.fields_get(cr, uid, context=context).iteritems():
            if 'context' in descriptor and 'fgroup' in descriptor['context']:
                fgroup = descriptor['context']['fgroup']
                elg = xarch.xpath('//group[@name="%(name)s"][@string="%(string)s"]' % fgroup)
                if elg:
                    _g = elg[0]
                else:
                    _g = etree.SubElement(group_values, 'group', name=fgroup['name'], string=fgroup['string'], colspan="2")
                el = etree.SubElement(_g, 'field', name=field)
            else:
                el = etree.SubElement(group_values, 'field', name=field)
            if field == 'time':
                # use our custom timepicker widget
                el.set('widget', 'timepicker')

        xarch, xfields = self._view_look_dom_arch(cr, uid, xarch, view_id, context=context)
        res['fields'] = xfields
        res['arch'] = xarch
        return res

report_birt_report_wizard()


class report_birt(report_int):
    def __init__(self, name, table, report_file):
        super(report_birt, self).__init__(name)
        self.table = table
        self.report_file = report_file

    def create(self, cr, uid, ids, values, context):
        headers = {'Content-type': 'application/json', 'Accept': 'application/octet-stream'}
        data = {
            'reportFile': self.report_file,
            'values': values,
        }

        api = tools.config.get_misc('birt-rs', 'api')
        report_api = os.path.join(api, 'report')

        r = requests.post(report_api, data=json.dumps(data, default=serialize), headers=headers)
        ext = re.search(r'filename=.+\.(.+);?', r.headers['content-disposition']).group(1)
        return (r.content, ext)


class registry(osv.osv):
    _inherit = 'ir.actions.report.xml'

    def register_all(self, cr):
        cr.execute("SELECT * FROM ir_act_report_xml WHERE auto=%s AND report_name LIKE 'birt.%%' ORDER BY id", (True,))
        result = cr.dictfetchall()
        svcs = netsvc.Service._services
        for r in result:
            if svcs.has_key('report.' + r['report_name']):
                continue
            report_birt('report.' + r['report_name'], r['model'], r['report_file'])
        super(registry, self).register_all(cr)
registry()
