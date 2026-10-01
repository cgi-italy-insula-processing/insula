cwlVersion: v1.0
$graph:
- class: Workflow
  label: Sentinel-2 product crop
  doc: This application crops bands from a Sentinel-2 product
  id: s2-cropper
  requirements:
  - class: ScatterFeatureRequirement
  inputs:
    bands:
      type: string[]
  outputs:
    results:
      outputSource:
      - node_crop/cropped_tif
      type: Directory[]
  steps:
    node_crop:
      run: "#crop-cl"
      in:
        band:
          source: [bands]   # <-- source is a LIST (ArrayList), not String
      out:
        - cropped_tif
      scatter: band
      scatterMethod: dotproduct
- class: CommandLineTool
  id: crop-cl
  requirements:
    DockerRequirement:
      dockerPull: ogc-crop:0.1
  baseCommand: crop
  arguments: []
  inputs:
    band:
      type: string
  outputs:
    cropped_tif:
      outputBinding:
        glob: .
      type: Directory
$namespaces:
  s: https://schema.org/
s:softwareVersion: 1.0.0
schemas:
- http://schema.org/version/latest/schemaorg-current-http.rdf
