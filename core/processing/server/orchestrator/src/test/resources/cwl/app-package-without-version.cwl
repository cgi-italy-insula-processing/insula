cwlVersion: v1.0
$graph:
- class: Workflow
  label: Sentinel-2 band crop
  doc: This application crops a Sentinel-2 band
  id: s2-cropper
  inputs:
    inputDirectory:
        type: Directory
  outputs:
    results:
        outputSource:
        - node_crop/cropped_tif
        type: Directory
  steps:
    node_crop:
        run: "#crop-cl"
        in:
            inputDirectory: inputDirectory
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
    inputDirectory:
        type: Directory
  outputs:
    cropped_tif:
        outputBinding:
            glob: .
        type: Directory
        label: Cropped band
        doc: Cropped Sentinel-2 band

$namespaces:
    s: https://schema.org/
$schemas:
- http://schema.org/version/latest/schemaorg-current-http.rdf