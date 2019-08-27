# mod-pubsub

Copyright (C) 2019 The Open Library Foundation

This software is distributed under the terms of the Apache License, Version 2.0.
See the file "[LICENSE](LICENSE)" for more information.


## Introduction

FOLIO publisher-subscriber module to provide event-driven approach.
It is responsible for maintaining the registrations of event types and for coordinating distribution of events to the appropriate subscribers (modules).

## Messaging descriptor

In order that the module can acting as Publishers and/or Subscribers should defined JSON config file on name `MessagingDescriptor.json`. 
This config file should contain a set of event types the module deals with. 
Publisher module should provide event descriptors for generated events. 
For subscriber module, should specifies a set of event type and callback endpoint for delivery events of this type.
\
MessagingDescriptor contains follow parts:
* publications - list of event descriptors describing events that this module produces, can be ommited if module does not generate any events.
* subscriptions - set of event types and endpoints (callback address) for receiving events of specified types, can be ommited if module does not receive any events.

\
MessagingDescriptor.json example:
```
{
  "publications": [
    {
      "eventType": "CREATED_SRS_MARC_BIB_RECORD_WITH_ORDER_DATA",
      "description": "Created SRS Marc Bibliographic Record with order data in 9xx fields",
      "eventTTL": 1,
      "signed": false
    }
  ],
  "subscriptions": [
    {
      "eventType": "CREATED_SRS_MARC_BIB_RECORD_WITH_ORDER_DATA",
      "callbackAddress": ""
    },
    {
      "eventType": "CREATED_SRS_MARC_BIB_RECORD_WITH_INVOICE_DATA",
      "callbackAddress": ""
    }
  ]
}
```

## Compiling

```
   mvn install
```

See that it says "BUILD SUCCESS" near the end.

## Docker

Build the docker container with:

```
   docker build -t mod-pubsub.
```

Test that it runs with:

```
   docker run -t -i -p 8081:8081 mod-pubsub
```

## Installing the module

Follow the guide of
[Deploying Modules](https://github.com/folio-org/okapi/blob/master/doc/guide.md#example-1-deploying-and-using-a-simple-module)
sections of the Okapi Guide and Reference, which describe the process in detail.

First of all you need a running Okapi instance.
(Note that [specifying](../README.md#setting-things-up) an explicit 'okapiurl' might be needed.)

```
   cd .../okapi
   java -jar okapi-core/target/okapi-core-fat.jar dev
```

We need to declare the module to Okapi:

```
curl -w '\n' -X POST -D -   \
   -H "Content-type: application/json"   \
   -d @target/ModuleDescriptor.json \
   http://localhost:9130/_/proxy/modules
```

That ModuleDescriptor tells Okapi what the module is called, what services it
provides, and how to deploy it.

## Deploying the module

Next we need to deploy the module. There is a deployment descriptor in
`target/DeploymentDescriptor.json`. It tells Okapi to start the module on 'localhost'.

Deploy it via Okapi discovery:

```
curl -w '\n' -D - -s \
  -X POST \
  -H "Content-type: application/json" \
  -d @target/DeploymentDescriptor.json  \
  http://localhost:9130/_/discovery/modules
```

Then we need to enable the module for the tenant:

```
curl -w '\n' -X POST -D -   \
    -H "Content-type: application/json"   \
    -d @target/TenantModuleDescriptor.json \
    http://localhost:9130/_/proxy/tenants/<tenant_name>/modules
```

## REST Client

Provides RMB generated Client to call the module's endpoints. The Client is packaged into the lightweight jar.

### Maven dependency 

```xml
    <dependency>
      <groupId>org.folio</groupId>
      <artifactId>mod-pubsub-client</artifactId>
      <version>x.y.z</version>
      <type>jar</type>
    </dependency>
```
Where x.y.z - version of mod-pubsub.

## Issue tracker

See project [MODPUBSUB](https://issues.folio.org/browse/MODPUBSUB)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).
