cwlVersion: v1.0
$graph:
- class: Workflow
  label: Sentinel-2 band crop
  doc: This application crops a Sentinel-2 band and is used to verify that the platform rejects CWL-derived service metadata when the description exceeds the maximum allowed persistence length of two hundred and fifty five characters for invalid input handling in the service deployment flow.
  id: s2-cropper
  inputs:
    product:
        type: string
  outputs:
    results:
        outputSource:
        - node_crop/cropped_tif
        type: Directory
  steps:
    node_crop:
        run: "#crop-cl"
        in:
            product: product
        out:
            - cropped_tif
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
        inputBinding:
            position: 1
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