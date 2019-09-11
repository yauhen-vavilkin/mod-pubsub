## 2019-07-02 v0.0.1-SNAPSHOT
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

 | METHOD |             URL                                | DESCRIPTION                                      |
 |--------|------------------------------------------------|--------------------------------------------------|
 | GET    | /pubsub/event-types                            | Get collection of Event Descriptors              |
 | POST   | /pubsub/event-types                            | Create new Event Type                            |
 | GET    | /pubsub/event-types/{eventTypeName}            | Get Event Descriptor of particular event type    |
 | PUT    | /pubsub/event-types/{eventTypeName}            | Update Event Descriptor of particular event type |
 | DELETE | /pubsub/event-types/{eventTypeName}            | Delete event type                                |
