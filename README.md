# mod-pubsub

Copyright (C) 2019-2022 The Open Library Foundation

This software is distributed under the terms of the Apache License, Version 2.0.
See the file "[LICENSE](LICENSE)" for more information.

* [Introduction](#introduction)
* [Compiling](#compiling)
* [Docker](#docker)
* [Installing the module](#installing-the-module)
* [Deploying the module](#deploying-the-module)
* [Environment variables](#environment-variables)
* [Verifying the module can connect and work with kafka](#verifying-the-module-can-connect-and-work-with-kafka)
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
****Make sure Kafka is up and running before deployment and pass all the required environment variables.****

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

## Environment variables
****Pubsub requires kafka to be running, and to ensure it can connect and interact with kafka the following environment variable must be specified on deployment:****
 ```
      {
        "name": "KAFKA_HOST",
        "value": "10.0.2.15"
      },
      {
        "name": "KAFKA_PORT",
        "value": "9092"
      },
      {
        "name": "OKAPI_URL",
        "value": "http://10.0.2.15:9130"
      }
```
****There are two additional parameters required for pubsub to create topics in kafka - number of partitions and replication factor.**** 
The replication factor controls how many servers will replicate each message that is written. If replication factor set to 3 then up to 2 servers can fail before access to the data will be lost.
The partition count controls how many logs the topic will be sharded into.

 ```
      {
        "name": "REPLICATION_FACTOR",
        "value": "3"
      },
      {
        "name": "NUMBER_OF_PARTITIONS",
        "value": "1"
      }
 ```   
If these values are not set then topics will be created with 1 partition and 1 replica. 

****In case a single Kafka installation is shared between multiple environments, make sure to set a customized prefix for kafka topics, specifying the environment in which pubsub is deployed****
This variable is used as a prefix to avoid any confusion with Kafka topics and consumer groups and is ****REQUIRED**** to prevent the situation when events are exchanged between pubsub instances belonging to different environments.
 ```
      {
        "name": "ENV",
        "value": "folio-testing"
      }
 ```
If this variable is not set, default "folio" prefix will be used in topic names, which is acceptable only if a separate Kafka installation is used on the environment.

****System user credentials**** 

`mod-pubsub` requires these credentials to be able to deliver events to subscribers.
 ```
      {
        "name": "SYSTEM_USER_NAME",
        "value": "pub-sub"
      },
      {
        "name": "SYSTEM_USER_PASSWORD",
        "value": "pubsub"
      }
 ```
Default username is `pub-sub`, password is `pubsub`.

## Verifying the module can connect and work with kafka

To verify that pubsub can successfully connect and work with kafka send the following requests:

1.POST /pubsub/event-types - register event type

```
curl --location --request POST 'http://localhost:9130/pubsub/event-types' \
--header 'X-Okapi-Tenant: <tenant>' \
--header 'X-Okapi-Token: <token>' \
--header 'Content-Type: application/json' \
--data-raw '{
    "eventType": "test-event",
    "description": "test",
    "eventTTL": 1,
    "signed": false
}'
```

2.POST /pubsub/declare/publisher - register publisher

```
curl --location --request POST 'http://localhost:9130/pubsub/event-types/declare/publisher' \
--header 'X-Okapi-Tenant: <tenant>' \
--header 'X-Okapi-Token: <token>' \
--header 'Content-Type: application/json' \
--data-raw '{
    "moduleId": "test-publisher",
    "eventDescriptors": [
        {
          "eventType": "test-event",
          "description": "test",
          "eventTTL": 1,
          "signed": false
        }
    ]
}'
```

3.POST /pubsub/declare/subscriber - register subscriber

```
curl --location --request POST 'http://localhost:9130/pubsub/event-types/declare/subscriber' \
--header 'X-Okapi-Tenant: <tenant>' \
--header 'X-Okapi-Token: <token>' \
--header 'Content-Type: application/json' \
--data-raw '{
    "moduleId": "test-subscriber",
    "subscriptionDefinitions": [
        {
          "eventType": "test-event",
          "callbackAddress": "/callback/address/example"
        }
    ]
}'
```

4.POST /pubsub/publish - publish an event

```
curl --location --request POST 'http://localhost:9130/pubsub/publish' \
--header 'X-Okapi-Tenant: <tenant>' \
--header 'X-Okapi-Token: <token>' \
--header 'Content-Type: application/json' \
--data-raw '{
    "id": "7d4f4ceb-4fc3-48cc-8b58-ff3c3862a12c",
    "eventType": "test-event",
    "eventMetadata": {
    	"tenantId": "diku",
    	"eventTTL": 1,
    	"publishedBy": "test-publisher"
    }
}'
```

5.GET /pubsub/history - check history to verify that event was processed

```
curl --location --request GET 'http://localhost:9130/pubsub/history?startDate=<start-date>&endDate=<end-date>&eventType=test-event' \
--header 'X-Okapi-Tenant: <tenant>' \
--header 'X-Okapi-Token: <token>'
```

There should be 4 auditMessages at this point, verify there are CREATED, RECEIVED, PUBLISHED, REJECTED (test callback endpoint does not exist) records.

## Memory allocation level
The appropriate container memory allocation level is 715827882 bytes, hence 66% of the container memory (472446402 bytes) will be reserved for java heap space.

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
      "callbackAddress": "/callback/address/example"
    },
    {
      "eventType": "CREATED_INVENTORY_INSTANCE",
      "callbackAddress": "/callback/address/example"
    }
  ]
}
```
In the example above the module will be registered as a Publisher for `CREATED_SRS_MARC_BIB_RECORD_WITH_ORDER_DATA` Events 
and as a Subscriber for `CREATED_MARCCAT_BIB_RECORD` and `CREATED_INVENTORY_INSTANCE` Events. In case either `CREATED_MARCCAT_BIB_RECORD` or `CREATED_INVENTORY_INSTANCE`
event is published a _POST_ request will be sent to _/callback/address/example_ endpoint with body provided by the Publisher of such Event in EventPayload section.

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

#### Simple workflow example
For example, we have 2 modules: publisher and subscriber.
Publisher publishes an event to the "mod-pubsub", which will be delivered to the subscriber module.
- First, we should register the first module as "publisher" in "mod-pubsub". To do so, we create a new file "MessagingDescriptor.json"  in the root folder:
```
{
  "publications": [
    {
      "eventType": "CREATED_TEST_EVENT",
      "description": "Created test event",
      "eventTTL": 1,
      "signed": false
    }
  ],
  "subscriptions": [
  ]
}
```

This way the module is being declared as publisher for the "CREATED_TEST_EVENT" type.


  
- Second, we should register the module in "mod-pubsub":
```java
public class ModTenantAPI extends TenantAPI {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModTenantAPI.class);

  @Override
  public void postTenant(TenantAttributes tenantAttributes, Map<String, String> headers, Handler<AsyncResult<Response>> handler, Context context) {
    super.postTenant(tenantAttributes, headers, postTenantAr -> {
      if (postTenantAr.failed()) {
        handler.handle(postTenantAr);
      } else {
        Vertx vertx = context.owner();
        vertx.executeBlocking(
          blockingFuture -> registerModuleToPubsub(headers, context.owner())
            .onComplete(event -> handler.handle(postTenantAr)),
          result -> handler.handle(postTenantAr)
        );
      }
    }, context);
  }

  private Future<Void> registerModuleToPubsub(Map<String, String> headers, Vertx vertx) {
    Promise<Void> promise = Promise.promise();
    PubSubClientUtils.registerModule(new org.folio.rest.util.OkapiConnectionParams(headers, vertx))
      .whenComplete((registrationAr, throwable) -> {
        if (throwable == null) {
          LOGGER.info("Module was successfully registered as publisher/subscriber in mod-pubsub");
          promise.complete();
        } else {
          LOGGER.error("Error during module registration in mod-pubsub", throwable);
          promise.fail(throwable);
        }
      });
    return promise.future();
  }
}
```

- In addition, modules registered as "publishers" and/or "subscribers" should add specific "pub-sub" permission as "modulePermissions" in "ModuleDescriptor-template.json":
  ```json
    "provides": [
      {
        "id": "_tenant",
        "version": "1.2",
        "interfaceType": "system",
        "handlers": [
          {
            "methods": [
              "POST"
            ],
            "pathPattern": "/_/tenant",
            "modulePermissions": [
              "pubsub.event-types.post",
              "pubsub.publishers.post",
              "pubsub.subscribers.post"
            ]
          },
          {
            "methods": [
              "DELETE"
            ],
            "pathPattern": "/_/tenant"
          }
        ]
      }
    ]
  ```

- Then, implement sending event anywhere in your code:
```
  private void publishEvent(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext, Event event) {
    Event event = new Event().
      withEventType("CREATED_TEST_EVENT")
      .withEventPayload("Test payload")
      .withEventMetadata(new EventMetadata()
        .withPublishedBy("mod-publisher")
        .withTenantId(okapiHeaders.get(OKAPI_TENANT_HEADER))
        .withEventTTL(1));
    OkapiConnectionParams params = new OkapiConnectionParams(okapiHeaders, vertxContext.owner());
    PubSubClientUtils.sendEventMessage(event, params)
      .whenComplete((result, throwable) -> {
        if (result) {
          LOGGER.info("Event published successfully: {}", event.getId());
          asyncResultHandler.handle(Future.succeededFuture());
        } else {
          LOGGER.error("Failed to publish event: {}", event.getId());
          asyncResultHandler.handle(Future.failedFuture("Failed to publish event"));
        }
      });
}
```


- After that, let's create subscriber module. At first, define "MessagingDescriptor.json"-file for subscriber module:
```json
{
  "publications": [
  ],
  "subscriptions": [
    {
      "eventType": "CREATED_TEST_EVENT",
      "callbackAddress": "/callback/address/example"
    }
  ]
}

```
Endpoint, which will receive the event from the publisher module has address: "/callback/address/example".

- Subscriber module needs to provide endpoints for all subscriptions listed in the `subscriptions` block of its `MessagingDescriptor.json`. 
Let's add an interface to the subscriber's `provides` block in the `ModuleDescriptor-template.json`. 
Note that each handler should have `pubsub.events.post` permission in the `requiredPermissions` which would allow `mod-pubsub` to call this endpoint.
```json
{
  "id": "{module-name}-event-handlers",
  "version": "{version}",
  "handlers": [
    {
      "methods": [
        "POST"
      ],
      "pathPattern": "/callback/address/example",
      "permissionsRequired": [
        "pubsub.events.post"
      ],
      "modulePermissions": [
        "required.permission.if.needed"
      ]
    }
  ]
}
```
- Register this subscriber module in the "mod-pubsub" the same way as first module via TenantAPI.

- Create new endpoint which was declared as "callbackAddress" using raml:
```raml
/callback/address/example:
    displayName: Publish event
    description: API used by subscriber to catch events
    post:
      body:
        application/json:
          type: string
      responses:
        204:
        400:
          body:
            application/json:
              schema: errors
        500:
          description: "Internal server error"
          body:
            text/plain:
              example: "Internal server error"
```
Then, let's create logic for logging event in subscriber's new endpoint which was declared as "callbackAddress" in the "MessagingDescriptor.json":
```java
public class ProcessRecordsImpl implements ProcessRecords {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessRecordsImpl.class);

  @Override
  public void postProcessRecords(String entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(Future.succeededFuture());
    LOGGER.info("Event: {}", entity);
  }
}
```
Keep in mind, that subscriber module should return response to the "mod-pubsub" before all business-logic. Main reasons for it:
- Extra connection to the module should be closed for best performance;
- There is no need for waiting response from subscriber's logic because this is not "mod-pubsub" responsibility. It should only deliver event to this module.

As a result, we can send an event from the publisher module to "mod-pubsub" and "mod-pubsub" will deliver it to the subscriber module.

#### Permissions

During tenant init (upgrade, enable), mod-pubsub checks whether the system user
(default username is "pub-sub", see **System user credentials** for more details) user exists in the system.
If the user does not exist, it is created and actived. In any case, permissions
from the file `pubsub-user-permissions.csv` are assigned to the system user.

##### When system user is logged in, its token is used for delivering events to subscriber module.

- In "mod-pubsub" user permissions are declared in "mod-pubsub-server/src/main/resources/permissions/pubsub-user-permissions.csv"

## Issue tracker

See project [MODPUBSUB](https://issues.folio.org/browse/MODPUBSUB)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).
