# I01c2b — Spring service/process foundation

Starting commit: `e938470b11ced68862d4ab3336fbfe226f6ec8dd`. Clean master/index;
D-304 authorization confirmed. No findings or due group review; I01c2a is implemented
and this is the earliest eligible child. No other tasks or gates advance.

Sources: I01c2b's unchanged outcome/checks; runtime profile, application container
interface; storage mechanics writer ownership. Coverage is the I01 foundation's
partial **P-001, T-015, T-551, T-630, T-662** allocation, specifically service
configuration/health/ownership/restart toward T-630/T-662. No full acceptance or
UI F01–F88 scenario is claimed executed. B1 review follows I01–I04 together.

Implement two explicitly configured Spring Boot servlet contexts (public/health),
file-backed database configuration, detail-free routes, periodic ownership recovery,
probed readiness and the shared fenced transaction admission boundary, with bounded
shutdown. Avoid automatic database/Actuator/environment/forwarded-header configuration.
Only the singleton foundation is recovered; future real workers must join readiness.
SMTP/public account routes and future resource overrides are not fabricated here.

Qualify Spring 4.1.1 stable public artifacts with JVM21, strict locks, existing
Flyway/jOOQ tests and real installed service processes against owned PostgreSQL17.
Exercise unknown routes/port isolation, empty/invalid schemas, competing writers,
outage and lock loss, configuration/secret redaction, actual SIGTERM and restart.
Retain outputs and exact owned-resource cleanup. Image checks stay I01c3; reusable
Compose and normal Android/device checks remain I01e/I01f/I35.
