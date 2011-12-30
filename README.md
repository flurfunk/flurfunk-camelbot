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
