#!/bin/bash

cd $(dirname $0)

lein clean && rm -rfv conexp-clj/ conexp-clj.zip && \
lein deps && lein jar && \
mkdir -p conexp-clj/lib/ && \
cp stuff/libs/*.jar conexp-clj/lib/ && cp stuff/libs/*.clj conexp-clj/lib && \
cp -r bin lib res AUTHORS LICENSE README conexp-clj && \
if [ -f conexp-clj.jar ]; then mv conexp-clj.jar conexp-clj-$(cat VERSION).jar; fi && \
mv conexp-clj-$(cat VERSION).jar conexp-clj/lib/conexp-clj-$(cat VERSION)-$(date +%Y%m%d.%H%M%S).jar && \
zip -r conexp-clj.zip conexp-clj
