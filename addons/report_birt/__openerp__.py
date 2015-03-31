# -*- coding: utf-8 -*-

{
    'name': 'Birt Report Adapter',
    'version': '1.0',
    'category': 'Tools',
    'description': """
This module allows users to generate reports designed in Eclipse Birt
=====================================================================

""",
    'author': 'Codekaki Systems (R49045/14)',
    'website': 'http://codekaki.com',
    'summary': 'Birt, Report',
    'depends': [],
    'data': [
        'wizard/report_birt_view.xml',
        'wizard/report_birt_data.xml',
    ],
    'css': [],
    'js': [
        'static/src/js/widgets.js',
    ],
    'images': [],
    'installable': True,
    'application': True,
    'auto_install': False,
}

# vim:expandtab:smartindent:tabstop=4:softtabstop=4:shiftwidth=4:
