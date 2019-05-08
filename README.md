# opencast-influxdb-adapter #

[![Build Status](https://travis-ci.com/opencast/opencast-influxdb-adapter.svg?branch=master)](https://travis-ci.com/opencast/opencast-influxdb-adapter)

Parse log files containing lecture views, query [Opencast](https://opencast.org) for episode metadata and push data points to [InfluxDB](https://www.influxdata.com).

## Release Model and Versioning ##

The project has regular releases. If you want to report a bug or request a feature, please open an issue here on Github. If you want to incorporate changes, open a pull request and target the `master` branch.

To keep things simple, the version number only consists of major and minor version. The major version number increases when configuration changes are necessary, or the behavior changes significantly. Smaller fixes or additions only affect the minor version number.

## How it works ##

When started, the adapter will read lines from a log file specified in the adapter’s configuration file. By default, it will only read and process lines that are appended to this file after the adapter was started. However, using a command line parameter (see below), you can start reading the file from its beginning. The adapter will respect log rotations.

For each line, the adapter will

  * analyze its components (time stamp, episode ID, organization, …).
  * filter it, for example, by excluding search engine bots.
  * filter it using a sliding time window (see below).
  * ask Opencast for the episode’s metadata via the External API.
  * push the resulting data point to InfluxDB.

## Command line parameters ##

    --config-file=/etc/opencast-influxdb-adapter.properties

Path to the configuration file (see below). Mandatory.

    --from-beginning

Read the specified log file from the beginning, then “tail” it.

## Configuration file ##

There’s a documented sample configuration file located in `docs/opencast-influxdb-adapter.properties`.

    influxdb.uri=http://localhost:8086

URI of the InfluxDB database

    influxdb.user=root

User name for logging into the InfluxDB database

    influxdb.password=root

Password for the InfluxDB database

    influxdb.db-name=opencast

Name of the InfluxDB database. Note that the adapter will *not* create the database.

    influxdb.log-level=info

Log level of the InfluxDB client. Possible values are `info` and `debug`.

    influxdb.retention-policy=infinite

The [retention policy](https://docs.influxdata.com/influxdb/v1.7/guides/downsampling_and_retention/) to use for the InfluxDB points. You can omit this, in which case the default retention policy will be used.

    log-file=/var/log/httpd/access_log

The actual log file to analzye and tail.

    adapter.view-interval-iso-duration=PT2H

This interval is used for the sliding window, see below. As the name implies, it’s in [ISO 8601](https://de.wikipedia.org/wiki/ISO_8601) duration format, so stuff like `PT5M` and `PT2H5M` are possible.

    adapter.log-configuration-file=logback-sample.xml

[Logback](https://logback.qos.ch/manual/configuration.html) configuration file for the adapter. You can leave this out, in which case only standard output and standard error are used for logging. This might make sense if you use systemd or something similar to control logging.

A sample logback configuration file is located in `docs/opencast-influxdb-adapter-logback.xml`.

    adapter.invalid-user-agents=curl,Apache

A comma-separated list of strings that must not be contained in the `User-Agent`. This can be used to filter out bots from search engines, for example. Currently case-sensitive. Can be empty, in which case, user agents are not a filter criterion.

    adapter.valid-file-extensions=.mp4,.wmv

A comma-separated list of valid file extensions (note that it has to contain the period). Can be empty, in which case the extension is not a filter criterion.

    opencast.external-api.uri=https://{organization}.api.opencast.com

The (External API) URI the adapter connects to to find out an episode’s metadata. If you have a multi-organization installation, you can use the placeholder `{organization}` in the URI. Otherwise, leave it out.

    opencast.external-api.user=admin

Opencast External API login credentials, user name.

    opencast.external-api.password=password

Opencast External API login credentials, password.

    opencast.series-are-optional=false

Set this to true if every episode must have a series assigned to it in your Opencast setup. In this case, a missing series is considered (and logged as) an error. Otherwise, it's just a normal data point.

    opencast.external-api.cache-expiration-duration=PT0M

The adapter has an optional cache included that stores event metadata for faster retrieval. It’s evicted time-based, and you can control the time after a cache entry has been *written* that it is evicted again. Note that the special value `PT0M` (or any duration that equates to zero) disables the cache.

## Sliding Window Mechanism ##

The adapter doesn’t simply count one line of the log file as one “view” and pushes it into InfluxDB. Rather, when it keeps a cache of “current views”, which is initially empty. When it encounters a new log line, it does the following…

  * It checks if this view – containing the episode’s ID, an IP address and an organization – is contained in the cache.
  * If it’s not contained, it adds it to the cache (including the timestamp).

After doing that, it tries to “evict” the cache. That is, it checks for views that are no longer current. This is simple: for each entry in the cache, check if the stored timestamp is older than `adapter.view-interval-iso-duration`. For all of these entries, remove them from the cache and generate one data point.

This means that the longer you set the `view-interval`, the less views you get, and vice-versa.

## Opencast ##

If Opencast is configured, the adapter will try to retrieve metadata for every event it encounters using the External API. Specifically, it will use the =/api/events/{episodeId}= endpoint, passing the episode ID and the configured authorization parameters.

Note that this endpoint is available as of version *1.3.0* of the External API.

Also, note that the user that is configured to query the External API has to have access to the episodes in the log files. This implies the user has `ROLE_ADMIN` or `ROLE_ORGANIZATION_ADMIN`.

## InfluxDB ##

### Supported versions ###

The adapter was tested on InfluxDB 1.7, although it should be fairly backwards-compatible, we cannot say for sure it’ll work with anything less than that version.

### Setup ###

The adapter assumes that an InfluxDB database was created (see the `influxdb.db-name` property). Let’s assume the database is really called `opencast`. To create it, simply execute (for example, using the `influx` command line tool):

``` sql
CREATE DATABASE opencast
```

The adapter writes into a measurement called `impressions`. Opencast, however, currently expects downsampled data from a separate measurement `impressions_daily`. Here, as the name implies, the data is downsampled to a daily resolution to save performance and disk space. To create this downsampled measurement, you can use the following script (of course, replacing the database name if you diverge from `opencast`):

``` sql
CREATE RETENTION POLICY "one_week" ON "opencast" DURATION 1w REPLICATION 1 DEFAULT
CREATE RETENTION POLICY "infinite" ON "opencast" DURATION INF REPLICATION 1
CREATE CONTINUOUS QUERY "cq_impressions_daily" ON "opencast" RESAMPLE EVERY 1h BEGIN SELECT SUM(value) as value INTO "infinite"."impressions_daily" FROM "impressions" GROUP BY time(1d), seriesId, episodeId, organizationId END
```

This creates two “retention policies”, `one_week` and `infinite` and a “continuous query” to fill the `impressions_daily` measurement – see the [InfluxDB documentation on downsampling and data retention](https://docs.influxdata.com/influxdb/v1.7/guides/downsampling_and_retention/) for more information on that.

The idea here is to store the downsampled data using the `infinite` retention policy (unless you want to throw away old statistical data – though InfluxDB is pretty concise when storing data), and to store the exact view counts in the `one_week` retention policy.

## Installation ##

Download the latest release’s `.jar` file and run it:

    java -jar $downloaded-release.jar --config-file=/etc/opencast-influxdb-adapter.properties

If you use [systemd](https://www.freedesktop.org/wiki/Software/systemd/), there’s a service file in `docs/opencast-influxdb-adapter.service`.

## Building from source ##

### Requirements ###

To build the adapter, you need:

  * Maven
  * JDK, at least version 1.8

To run the adapter, you need:

  * JRE, at least version 1.8

### Building ###

To build the adapter, execute the following:

``` shell
mvn package
```

You’ll end up with a `tar.gz` in the `build/` directory, containing…

  * a runnable `jar` file
  * a systemd service file
  * a `logback.xml` file
