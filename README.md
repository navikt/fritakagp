# Fritak Arbeidsgiverperiode (AGP)

Backend for mottak av søknader om fritak fra AGP ved sykepenger.


## Komme i gang

For å kjøre lokalt kan du starte  `docker compose up` fra docker/local før du starter prosjektet. 

http://localhost:8080/local/token-please?subject=12345678910

Kopier token og sett som bearer token i feks Postman. 
Deretter kan du kalle: 

http://localhost:8080/fritak-agp-api/api/v1/arbeidsgivere


## URL i dev

https://arbeidsgiver.intern.dev.nav.no/fritak-agp/nb


## Koble til databasen i GCP

Følg oppskriften for [Cloud SQL proxy](https://doc.nais.io/reference/cli/postgres/).

For å koble til når du har personlig bruker:
```
CONNECTION_NAME=$(gcloud sql instances describe fritakagp --format="get(connectionName)" --project helsearbeidsgiver-dev-6d06);
./cloud_sql_proxy -instances=${CONNECTION_NAME}=tcp:5555
gcloud auth print-access-token
```

Koble til localhost:5555 med nav epost og access tokenet som blir printa over 


## Henvendelser

Spørsmål knyttet til koden eller prosjektet kan stilles som [issues](https://github.com/navikt/fritakagp/issues/new) her på GitHub.

### For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen [#helse-arbeidsgiver](https://nav-it.slack.com/archives/CSMN6320N).
