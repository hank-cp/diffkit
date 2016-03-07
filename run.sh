#!/bin/bash

scriptPath=$(readlink -f "$0")
cd $(dirname "$scriptPath")
java -Dfile.encoding=UTF-8 -jar diffkit-app.jar -planfiles dbConnectionInfo.xml,proofhead_stock_in_zs.plan.xml
java -Dfile.encoding=UTF-8 -jar diffkit-app.jar -planfiles dbConnectionInfo.xml,proofhead_stock_in_yy.plan.xml

find ./*.diff.* -mtime +10 -type f -delete