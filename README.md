tc-init-health-check-listener
=============================

Tomcat Initialization Health Check Lifecycle Listener

## Why

There is no easy way to check from a shell script if all Tomcat components (i.e.: Server, Service(s), Connector(s), Executor(s), Container and Context(s)) initialized properly during startup.

## How

This lifecycle listener waits for a `Lifecycle.AFTER_START_EVENT` from the Server component. When received, the `LifeCycleState` of each Tomcat component is checked.

Optionally, it is possible to notify an external process (e.g.: the startup script) via a FIFO or with a UDP packet and/or trigger a server shutdown upon failure.

### Version

This version (8.0.32) was built and tested against Tomcat 8.0.32, but should work with newer versions as well.

### Build

```bash
git clone https://github.com/goldendeal/tc-init-health-check-listener.git
cd tc-init-health-check-listener
mvn package
```

### Maven

```xml
<dependency>
  <groupId>gr.xe</groupId>
  <artifactId>tc-init-health-check-listener</artifactId>
  <version>8.0.32</version>
  <packaging>jar</packaging>
</dependency>
```

### Install

Copy `tc-init-health-check-listener/target/tc-init-health-check-listener-<version>.jar` to `${CATALINA_HOME}/lib` or `${CATALINA_BASE}/lib`

## Usage

In your server.xml add a server lifecycle listener:

```xml
<?xml version='1.0' encoding='utf-8'?>
<Server port="8005">
  <Listener className="gr.xe.dc.tomcat.listeners.InitHealthCheckListener"/>
  ...
</Server>
```

### Available Attributes

Attribute         | Type    | Default Value                | Description
:-----------------|:--------|:-----------------------------|:-----------
shutdownOnFailure | boolean | true                         | Trigger server shutdown on failure
notifyFIFO        | String  | none                         | A FIFO where the initialization status will be written
notifyAddress     | String  | localhost                    | Destination address of the UDP packet containing the initialization status
notifyPort        | Integer | Same as Server shutdown port | Destination port of the UDP packet containing the initialization status

### Shell usage

The following have been tested with bash.

#### FIFO

If using notifyFIFO:

```bash
read -t <timeout> STATUS <> /path/to/FIFO

if [ $? -ne 0 ]; then
  echo "Timed out"
  exit 1
fi

if [ "${STATUS}" -eq 0 ]; then
  echo "Success"
  exit 0
else
  echo "Failure"
  exit 1
fi
```

#### UDP

If using notifyAddress and notifyPort:

```bash
STATUS=$(timeout <timeout> socat UDP-RECVFROM:<notify port> STDOUT)

if [ $? -ne 0 ]; then
  echo "Timed out"
  exit 1
fi

if [ "${STATUS}" -eq 0 ]; then
  echo "Success"
  exit 0
else
  echo "Failure"
  exit 1
fi
```
