# Hibernate WildFly Test Case Templates

When trying to reproduce an issue for a project involving hibernate deployed in WildFly or EAP, in order to spot if the root cause of the issue comes from Hibernate it's extremely helpful to have a test case that does involve only Hibernate with all the configuration settings toggled on as WildFly/EAP does.  

The aim of this project is to help to create such a test cases.

To reproduce an issue related to WildFly using Hibernate ORM 5.1 a test should be added to the ORM51 subproject while for Hibernate ORM 5.3 a test should be added to ORM53 subproject.

Each subproject has 2 folders:
* `src/integration-test` for tests that will be run inside WildFly
* `src/test` for unit tests.

## IntelliJ

Before running/debugging integration tests from IntelliJ :
* to run a `./gradlew integrationTest`
* select `settings>Build Tools>Gradle` and check both `Build and run using` and `Run tests using` values are equals to`IntelliJ IDEA`

To debug an integration test in IntelliJ it is also required:

* in the `arquilliam.xml` file comment the code `<property name="javaVmArguments">-Djava.net.preferIPv4Stack=true -Djgroups.bind_addr=127.0.0.1</property>` and uncomment `<property name="javaVmArguments">-Djava.net.preferIPv4Stack=true -Djgroups.bind_addr=127.0.0.1 -Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y</property>` (Remember to revert the changes made to `arquilliam.xml`in order to run and not debug the integration tests)
* in `Run>Edit Configurations ...` add a new Remote configuration
* run the integration test
* select the created remote config and run debug
