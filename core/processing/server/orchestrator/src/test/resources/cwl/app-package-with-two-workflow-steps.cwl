cwlVersion: v1.0
$graph:
- class: Workflow
  label: Sentinel-2 band crop
  doc: This application crops a Sentinel-2 band
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
    second_step:
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