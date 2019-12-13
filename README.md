# mod-pubsub

Copyright (C) 2019 The Open Library Foundation

This software is distributed under the terms of the Apache License, Version 2.0.
See the file "[LICENSE](LICENSE)" for more information.

* [Introduction](#introduction)
* [Compiling](#compiling)
* [Docker](#docker)
* [Installing the module](#installing-the-module)
* [Deploying the module](#deploying-the-module)
* [Database schemas](#Database-schemas)
* [PubSub Client](#PubSub-Client)

## Introduction

The mod-pubsub module is responsible for maintaining the registration of EventTypes and for coordinating distribution of Events to the appropriate Subscribers. 
Folio modules may publish Events and/or subscribe to Events of specific EventTypes. It is the responsibility of Publisher modules to define the EventTypes that they will provide, 
which is accomplished through the use of an EventDescriptor. Note that it is possible for multiple Publishers to register for the same EventType. 
However, the EventType may only be defined once. Subscribing modules will register themselves to specific EvenTypes by providing a URI that will be invoked by the event manager when pushing events of matching EvenTypes. 
Event subscription is done on a per tenant basis.

The module also maintains a trail of activities: Event publication, subscription distribution, errors, non-delivery, etc.

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
Make sure Kafka is up and running on 9092 port before deployment.

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

## Database schemas

The mod-pub-sub module uses relational approach and Liquibase to define database schemas. 
It contains a non-tenant (`pubsub_config`) database schema for storing module config data not related to tenants. 
This module schema is created at the time of module initialization.

Database schemas are described in Liquibase scripts using XML syntax.
Every script file should contain only one "databaseChangeLog" that consists of at least one "changeset" describing the operations on tables. 
Scripts should be named using following format:
`yyyy-MM-dd--hh-mm-schema_change_description`.  \
`yyyy-MM-dd--hh-mm` - date of script creation;  \
`schema_change_description` - short description of the change.

Each "changeset" should be uniquely identified by the `"author"` and `"id"` attributes. It is advised to use the Github username as `"author"` attribute. 
The `"id"` attribute value should be defined in the same format as the script file name.  

If needed, database schema name can be obtained using Liquibase context property `${database.defaultSchemaName}`.

Liquibase scripts are stored in `/resources/liquibase/` directory. 
Scripts files for module and tenant schemas are stored separately in `/resources/liquibase/module/scripts` and `/resources/liquibase/tenant/scripts` respectively. 
\
To simplify the tracking of schemas changes, the module versioning is displayed in the directories structure:
```
/resources/liquibase
    /module/scripts
              /v-1.0.0
                  /2019-08-14--14-00-create-module-table.xml
              /v-2.0.0
                  /2019-09-03--11-00-change-id-column-type.xml                        
    /module/scripts
              /v-1.0.0
                  /2019-09-06--15-00-create-audit_message-table.xml
```

## PubSub Client

PubSub Client provides utility methods for registering modules as Publishers and/or Subscribers in pub-sub and sending Events.
It is packaged into a lightweight jar and can be added to the module dependencies as follows:

#### Maven dependency 

```xml
    <dependency>
      <groupId>org.folio</groupId>
      <artifactId>mod-pubsub-client</artifactId>
      <version>x.y.z</version>
      <type>jar</type>
    </dependency>
```
Where x.y.z - version of mod-pubsub.

#### Messaging descriptor

Each module which acts as a Publisher or a Subscriber should contain a `MessagingDescriptor.json` file. 
Location of this file can be specified in `messaging_config_path` system property. If such property is not set or file cannot be found there, it will be searched for in classpath.

`MessagingDescriptor.json` file should contain the following properties:
* `publications` - list of EventDescriptors that current module can publish. The list can be empty if the module does not publish any Events.
* `subscriptions` - list of SubscriptionDefinitions, each containing an EventType and callback address for receiving an Event payload. 
The list can be empty if module does not subscribe to receive any Events.

MessagingDescriptor.json example:
```json
{
  "publications": [
    {
      "eventType": "CREATED_SRS_MARC_BIB_RECORD_WITH_ORDER_DATA",
      "description": "Created SRS Marc Bibliographic Record with order data in 9xx fields",
      "eventTTL": 10,
      "signed": false
    }
  ],
  "subscriptions": [
    {
      "eventType": "CREATED_MARCCAT_BIB_RECORD",
      "callbackAddress": "/source-storage/records"
    },
    {
      "eventType": "CREATED_INVENTORY_INSTANCE",
      "callbackAddress": "/source-storage/records"
    }
  ]
}
```
In the example above the module will be registered as a Publisher for `CREATED_SRS_MARC_BIB_RECORD_WITH_ORDER_DATA` Events 
and as a Subscriber for `CREATED_MARCCAT_BIB_RECORD` and `CREATED_INVENTORY_INSTANCE` Events. In case either `CREATED_MARCCAT_BIB_RECORD` or `CREATED_INVENTORY_INSTANCE`
event is published a _POST_ request will be sent to _/source-storage/records_ endpoint with body provided by the Publisher of such Event in EventPayload section.

#### Module registration in pub-sub

The module should be registered in pub-sub at the time when it is being enabled for a tenant. To do so `PubSubClientUtils` class provides `registerModule` method, 
which can be invoked in TenantAPI implementation. Example:

```
  @Override
  public void postTenant(TenantAttributes tenantAttributes, Map<String, String> headers, Handler<AsyncResult<Response>> handler, Context context) {
    super.postTenant(tenantAttributes, headers, postTenantAr -> {
      if (postTenantAr.failed()) {
        handler.handle(postTenantAr);
      } else {
        Vertx vertx = context.owner();
        vertx.executeBlocking(
          blockingFuture -> {
            OkapiConnectionParams params = new OkapiConnectionParams(headers, vertx);
            PubSubClientUtils.registerModule(params);
            blockingFuture.complete();
          },
          result -> handler.handle(postTenantAr)
        );
      }
    }, context);
  }
``` 

#### Event publishing

To publish an event `PubSubClientUtils` class provides `sendEventMessage` method. Example usage:

```
  @Override
  public void postSample(Entity entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    Event event = new Event()
      .withId(UUID.randomUUID().toString())
      .withEventType("CREATED_SRS_MARC_BIB_RECORD_WITH_ORDER_DATA")
      .withEventPayload(JsonObject.mapFrom(entity).encode())
      .withEventMetadata(new EventMetadata()
        .withPublishedBy("mod-sample-publisher")
        .withEventTTL(30)
        .withTenantId(okapiHeaders.get(OKAPI_TENANT_HEADER)));
    OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders, vertxContext.owner());
    PubSubClientUtils.sendEventMessage(event, params)
      .whenComplete((result, throwable) -> {
        if (result) {
          asyncResultHandler.handle(Future.succeededFuture(PostSampleResponse.respond201()));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(PostSampleResponse.respond500WithTextPlain("Failed to publish event")));
        }
      });
  }
```

## Issue tracker

See project [MODPUBSUB](https://issues.folio.org/browse/MODPUBSUB)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).
