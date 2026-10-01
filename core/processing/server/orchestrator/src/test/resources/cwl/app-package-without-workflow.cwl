cwlVersion: v1.0
$graph:
- class: CommandLineTool
  id: crop-cl
  requirements:
    DockerRequirement:
        dockerPull: ogc-crop:0.1
    ResourceRequirement:
        coresMin: 2
        ramMin: 2
  baseCommand: crop
  arguments: []
  inputs:
    product:
        type: string
  outputs:
    cropped_tif:
        outputBinding:
            glob: .
        type: Directory

$namespaces:
    s: https://schema.org/
s:softwareVersion: 1.0.0
$schemas:
- http://schema.org/version/latest/schemaorg-current-http.rdf