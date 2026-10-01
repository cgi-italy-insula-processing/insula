cwlVersion: v1.2
$graph:
- class: Workflow
  label: FastCopier Files CWL
  doc: This CWL creates a service that refers to a basic StageIn+StageOut FastCopier
  id: FastCopierFilesCWL
  inputs:
    in:
      type: File

  outputs:
    out:
      type: File
      format: GeoTIFF
      outputSource: process/out

  steps:
    process:
      run: '#main'
      in:
        in: in
      out:
      - out

- class: CommandLineTool
  id: main
  requirements:
    DockerRequirement:
      dockerPull: eopaas/fastcopierfiles:latest
    NetworkAccess:
      networkAccess: true
    EnvVarRequirement:
      envDef:
        PATH: /opt/conda/bin:/opt/conda/condabin:/opt/conda/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
  baseCommand: /home/worker/processor/workflow.sh
  inputs:
    in:
      type: File
      inputBinding:
        position: 1

  outputs:
    out:
      outputBinding:
        glob: ./outDir
      type: File
      format: GeoTIFF

$namespaces:
  s: https://schema.org/
$schemas:
- http://schema.org/version/9.0/schemaorg-current-http.rdf
