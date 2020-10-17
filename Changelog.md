# Changelog

## 0.1.9-SNAPSHOT

### Added
 - parallel processing namespace (using Claypoole)
 
### Breaking
 - `core/create-consumer` no longer accepts a variable number of arguments, instead requires an object of options. Other namespaces remain unchanged


## 0.2.0

### Breaking
Simplifying how to create a consumer. When creating a consumer from the `sequential` namespace it is now no longer required to wrap your processing function with another call to `sequential-process`. This is also true for the `batch` and `parallel` namespaces.
