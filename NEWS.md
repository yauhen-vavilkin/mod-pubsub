## 2019-01-21 v1.0.2
* Fixed reading "MessagingDescriptor" file from JAR file

## 2019-12-13 v1.0.1
* Removed default permissions for pub-sub user
* Updated LaunchDescriptor
* Used new base docker image
* Updated documentation

## 2019-12-04 v1.0.0
* Initial module setup
* Defined EventDescriptor and Event schemas
* Changed project structure to contain server and client parts. Client builds as a lightweight java library
* Extended Event schema
* Added samples
* Applied Liquibase scripting tool to manage database tables
* Applied Spring DI maintenance
* Added stub implementations for EventService and EventDao
* Added scripts to create module specific tables: module, event_type, messaging_module
* Defined MessagingDescriptor, PublisherDescriptor and SubscriberDescriptor schemas.
* Added PubSubClientUtil to read MessagingDescriptor file.
* Added schemas for audit trail
* Added Dao components for module schema.
* Added DAO component for tenant schema
* Added API for Event Types managing
* Added API for Publishers managing
* Added API for Subscribers managing
* Created API for retrieving audit messages
* Added preliminary cleaning of publisher/subscriber information before declaration publisher/subscriber with same module name and tenant id   
* Configured kafka client
* Removed module table, modified messagingModule table, updated schemas
* Created Publishing service
* Created Consumer service
* Created Security Manager
* Created Startup Service

 | METHOD |             URL                                                                         | DESCRIPTION                                      |
 |--------|-----------------------------------------------------------------------------------------|--------------------------------------------------|
 | GET    | /pubsub/event-types                                                                     | Get collection of Event Descriptors              |
 | POST   | /pubsub/event-types                                                                     | Create new Event Type                            |
 | GET    | /pubsub/event-types/{eventTypeName}                                                     | Get Event Descriptor of particular event type    |
 | PUT    | /pubsub/event-types/{eventTypeName}                                                     | Update Event Descriptor of particular event type |
 | DELETE | /pubsub/event-types/{eventTypeName}                                                     | Delete event type                                |
 | POST   | /pubsub/event-types/declare/publisher                                                   | Create publisher                                 |
 | DELETE | /pubsub/event-types/{eventTypeName}/publisher?moduleName={moduleName}                   | Delete publisher declaration                     |
 | GET    | /pubsub/event-types/{eventTypeName}/publishers                                          | Get collection of Publishers                     |
 | POST   | /pubsub/event-types/declare/subscriber                                                  | Create subscriber                                |
 | DELETE | /pubsub/event-types/{eventTypeName}/subscribers?moduleName={moduleName}                 | Delete subscriber declaration                    |
 | GET    | /pubsub/event-types/{eventTypeName}/subscribers                                         | Get collection of Subscribers                    |
 | GET    | /pubsub/history?startDate={startDate}&endDate={endDate}                                 | Retrieve activity history for a period of time   |
 | GET    | /pubsub/audit-messages/{eventId}/payload                                                | Get audit message payload by eventId             |
