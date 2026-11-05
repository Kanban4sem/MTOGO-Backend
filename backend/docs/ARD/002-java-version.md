# ARD-002: Java Version

## Status

Approved by:

- [X] Jonas
- [ ] Jesper
- [ ] Daniel
- [ ] Dino

## Context
We need a Java version that is compatible with our chosen Framework - Spring Boot 4. As for my - Jonas' - understanding Java 25 is less compatible with Spring Boot 4 as old dependencies have not been fully merged to the newest Java 25 version. Upon further research it seems to be okay and working as intended as per 05/11-2026.

## Decision
We as a team has desided on using Java 25 as our JDK / SDK for this MTOGO project as a starting point. It is compatible with Spring Boot 4 and has the functionality that we as a team seek in order to complete our project.

## Alternatives

**Java 21**

- Great version
- Compatible with Spring Boot
- Has functionality and older dependencies + naming

**Java 17**

- Our most used and known JDK
- Been used for the past 4 semesters

## Consequences

- Some individuals on our team might not know 25 as much as 21 or 17 for example.
- We are ready to learn and work with 25 but it requires a little more effort from each member.
- ~130mb download in case it wasn't downloaded already.