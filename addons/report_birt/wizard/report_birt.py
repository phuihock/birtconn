# -*- encoding: utf-8 -*-

from openerp.osv import fields, osv
from lxml import etree
from openerp.report.interface import report_int
from openerp import netsvc, tools
from copy import copy
import requests
import simplejson as json
import subprocess
import os

class report_birt_report_wizard(osv.osv_memory):
    _name = 'report_birt.report_wizard'
    _description = 'Birt Report Wizard'

    _columns = {
        'report_name': fields.char(size=64, string="Report Name"),
        'parameters': fields.text(string="Parameters"),
    }

    def fields_get(self, cr, uid, allfields=None, context=None, write_access=True):
        res = super(report_birt_report_wizard, self).fields_get(cr, uid, allfields, context, write_access)
        for f in self._columns:
            res[f]['invisible'] = True

        if 'report_name' in context:
            report_obj = self.pool.get('ir.actions.report.xml')
            found = report_obj.search(cr, uid, [('report_name', '=', context['report_name'])])
            if found:
                report_id = found[0]
                report = report_obj.read(cr, uid, report_id, ['report_file'])

                api = tools.config.get_misc('birtws', 'api')
                report_api = os.path.join(api, 'report')
                r = requests.get('%s?report_file=%s' % (report_api, report['report_file']))
                parameters = r.json()
                for param in parameters:
                    name = param['name']
                    meta = {}
                    if param['controlType'] == 'list box':
                        meta['type'] = 'selection'
                        meta['selection'] = param['selection']
                    else:
                        meta['type'] = param['dataType']
                    meta['string'] = param['promptText']
                    meta['required'] = param['required']
                    meta['help'] = param['helpText']
                    res[name] = meta
        return res

    def create(self, cr, uid, vals, context=None):
        # 'vals' contains all field/value pairs, including report_name and parameters.
        # But we don't have all the columns since they are dynamically generated
        # based on report's parameters.
        parameters = dict(filter(lambda (x, y): x not in ['report_name', 'parameters'], vals.items()))
        parameters = json.dumps(parameters)
        vals = {
            'report_name': context['report_name'],
            'parameters': parameters,
        }
        return super(report_birt_report_wizard, self).create(cr, uid, vals, context)

    def print_report(self, cr, uid, ids, context=None):
        parameters = self.read(cr, uid, ids[0], ['parameters'], context=context)['parameters']
        parameters = json.loads(parameters)
        return {
            'type': 'ir.actions.report.xml',
            'report_name': context['report_name'],
            'datas': parameters,
        }

    def fields_view_get(self, cr, uid, view_id=None, view_type='form', context=None, toolbar=False, submenu=False):
        res = super(report_birt_report_wizard, self).fields_view_get(cr, uid, view_id, view_type, context, toolbar, submenu)
        if view_type == 'search':
            return res

        xarch = etree.XML(res['arch'])
        group = xarch.xpath("//group")[0]

        for field, descriptor in self.fields_get(cr, uid, context=context).iteritems():
            etree.SubElement(group, 'field', name=field)
            if descriptor['type'] == 'text':
                etree.SubElement(group, 'newline')

        xarch, xfields = self._view_look_dom_arch(cr, uid, xarch, view_id, context=context)
        res['fields'] = xfields
        res['arch'] = xarch
        return res

report_birt_report_wizard()


class report_birt(report_int):
    def __init__(self, name, table, report_file, report_type):
        super(report_birt, self).__init__(name)
        self.table = table
        self.report_file = report_file
        self.report_type = report_type

    def create(self, cr, uid, ids, parameters, context):
        headers = {'Content-type': 'application/json', 'Accept': 'application/octet-stream'}
        data = {
            'report_file': self.report_file,
            'arguments': parameters,
            'format': self.report_type,
        }

        api = tools.config.get_misc('birtws', 'api')
        report_api = os.path.join(api, 'report')
        r = requests.post(report_api, data=json.dumps(data), headers=headers)
        return (r.content, self.report_type)


class registry(osv.osv):
    _inherit = 'ir.actions.report.xml'

    def register_all(self, cr):
        cr.execute("SELECT * FROM ir_act_report_xml WHERE auto=%s AND report_name LIKE 'birt.%%' ORDER BY id", (True,))
        result = cr.dictfetchall()
        svcs = netsvc.Service._services
        for r in result:
            if svcs.has_key('report.' + r['report_name']):
                continue
            report_birt('report.' + r['report_name'], r['model'], r['report_file'], r['report_type'])
        super(registry, self).register_all(cr)
registry()
