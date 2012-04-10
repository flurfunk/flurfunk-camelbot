Flurfunk - CamelBot
===================

Camelbot is a generic Flurfunk-bot that uses Apache Camel.

Run it locally like this:

        mvn camel:run

Generating a standalone camelbot application/daemon
---------------------------------------------------

        mvn package

This will create a zipfile in the target directory. This artifact is continously deployed into
Nexus.

Running camelbot
----------------

        sh camelbot_dir/bin/camelbot


Configuration
-------------

You can either specify -DcamelProps=path-to-file in JAVA_OPTS before running, or a properties 
file in /etc/camelbot.properties will be assumed.

See src/main/config/camelbot.properties for available properties you can override.


License
-------

Copyright 2012 Viaboxx GmbH

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
