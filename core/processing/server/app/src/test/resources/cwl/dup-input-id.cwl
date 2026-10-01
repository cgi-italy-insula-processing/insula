cwlVersion: v1.2
$graph:
- class: Workflow
  label: FastCopierDupIn
  doc: This CWL is used for testing duplicated Id check in inputs (POIEO-4906)
  id: FastCopierInput4906
  inputs:
    in:
      doc:
      label: IN
      type: Directory
    in:
      doc:
      label: IN
      type: Directory

  outputs:
    out:
      type: Directory
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
      dockerPull: eopaas/fastcopierstageinout:latest
    NetworkAccess:
      networkAccess: true
    EnvVarRequirement:
      envDef:
        PATH: /opt/conda/bin:/opt/conda/condabin:/opt/conda/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
  baseCommand: /home/worker/processor/workflow.sh
  inputs:
    in:
      type: Directory
      inputBinding:
        position: 1
    in:
      type: Directory
      inputBinding:
        position: 2

  outputs:
    out:
      outputBinding:
        glob: ./outDir/out
      type: Directory
$namespaces:
  s: https://schema.org/
  s:author: arthurduf
  s:contributor: arthurduf
  s:citation: https://github.com/MAAP-Project/sardem-sarsen.git
  s:codeRepository: https://github.com/MAAP-Project/sardem-sarsen.git
  s:dateCreated: 2025-02-18
  s:license: https://github.com/MAAP-Project/sardem-sarsen/blob/main/LICENSE
  s:softwareVersion: 1.1.0
  s:version: mlucas/nasa_ogc
  s:releaseNotes: None
  s:keywords: ogc, sar
$schemas:
- http://schema.org/version/9.0/schemaorg-current-http.rdf
