param([ValidateRange(1024, 65535)][int]$Port = 18080)
if ($Port -eq 8080) { throw 'Use a separate port so the main application remains available.' }
$repositoryRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $repositoryRoot
try {
    & ./mvnw.cmd -B -q spring-boot:run '-Dspring-boot.run.useTestClasspath=true' "-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=$Port --app.demo.enabled=true --app.admin.key= --app.upload.dir=./target/browser-test-uploads --spring.datasource.url=jdbc:h2:mem:browser-test;MODE=MySQL;DB_CLOSE_DELAY=-1 --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.datasource.password= --spring.jpa.hibernate.ddl-auto=create-drop --spring.jpa.open-in-view=false"
    if ($LASTEXITCODE -ne 0) { throw "Browser test server exited with $LASTEXITCODE" }
} finally { Pop-Location }
