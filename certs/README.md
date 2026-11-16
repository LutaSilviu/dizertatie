# Extra trusted certificates (optional)

Leave this folder as it is unless image builds fail with a certificate error
(`PKIX path building failed`, `unable to verify the first certificate`,
`self-signed certificate in certificate chain`).

That error means HTTPS traffic on your machine is inspected by an antivirus "web shield" or a
corporate proxy, which re-signs it with its own root certificate. Put that root certificate here
as a PEM file ending in `.crt` and rebuild:

```bash
docker compose up --build
```

The build adds every `*.crt` in this folder to the trust stores used inside the images (Java,
Node and Chromium). Certificate checking stays on. The certificates are specific to your machine
and are ignored by git.

On Windows, export the certificate from *Manage computer certificates > Trusted Root
Certification Authorities* as "Base-64 encoded X.509", and save it here with the `.crt` extension.
