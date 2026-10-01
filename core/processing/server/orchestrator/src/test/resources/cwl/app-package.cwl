cwlVersion: v1.0
$graph:
- class: Workflow
  label: Sentinel-2 band crop
  doc: This application crops a Sentinel-2 band
  id: s2-cropper
  inputs:
    inputDirectory:
        type: Directory
        label: Sentinel-2 inputs
        doc: Sentinel-2 Level-1C or Level-2A input reference
    inputStringWithDefault:
        type: string
        default: "def"
        label: Sentinel-2 band
        doc: Sentinel-2 band to crop (e.g. B02)
    OptFloat:
        type: float?
        label: opt float
        doc: optional float
    inputEnum:
        type:
          type: enum
          symbols:
            - "bam"
            - "sam"
        label: enum
        doc: enum type
    inputArray:
        type:
          type: array
          items: string
    inputInt:
        type: int
    inputLong:
        type: long
    inputFile:
        type: File
    inputBool:
        type: boolean
    OptArray:
        type:
          - "null"
          - type: array
            items: string
    OptEnum:
        type:
          - "null"
          - type: enum
            symbols:
              - "vam"
              - "ram"
    inputStringWithoutPosition:
        type: string
  outputs:
    results:
        outputSource:
        - node_crop/cropped_tif
        type: Directory
    results_outputSource_flat:
        outputSource: node_crop/flat_cropped_tif
        type: Directory
    file_output:
        outputSource: node_crop/file_output
        type: File
  steps:
    node_crop:
        run: "#crop-cl"
        in:
            inputDirectory: inputDirectory
            inputStringWithDefault: inputStringWithDefault
            OptFloat: OptFloat
            inputEnum: inputEnum
            inputArray: inputArray
            inputInt: inputInt
            inputLong: inputLong
            inputFile: inputFile
            inputBool: inputBool
            OptArray: OptArray
            OptEnum: OptEnum
            inputStringWithoutPosition: inputStringWithoutPosition
        out:
            - cropped_tif
            - flat_cropped_tif
            - file_output
- class: CommandLineTool
  id: crop-cl
  requirements:
    InitialWorkDirRequirement:
      listing:
        - class: Directory
          location: ./mount/userMount
          basename: home/first
    DockerRequirement:
        dockerPull: ogc-crop:0.1
    ResourceRequirement:
        coresMin: 2
        ramMin: 2
    NetworkAccess:
        networkAccess: true
    EnvVarRequirement:
        envDef:
            PATH: /opt/sbin:/bin
  baseCommand: crop
  arguments: [arg1, arg2]
  inputs:
    inputDirectory:
        type: Directory
        label: Sentinel-2 inputs
        doc: Sentinel-2 Level-1C or Level-2A input reference
        inputBinding:
            position: 1
    inputStringWithDefault:
        type: string
        default: "def"
        inputBinding:
            position: 2
            prefix: --prefix
    OptFloat:
        type: float?
        label: opt float
        doc: optional float
        inputBinding:
            position: 3
    inputEnum:
        type:
          type: enum
          symbols:
            - bam
            - sam
        label: enum
        doc: enum type
        inputBinding:
            position: 4
    inputArray:
        type:
          type: array
          items: string
        inputBinding:
            position: 5
    inputInt:
        type: int
        inputBinding:
            position: 6
    inputLong:
        type: long
        inputBinding:
            position: 7
    inputFile:
        type: File
        inputBinding:
            position: 8
    inputBool:
        type: boolean
        inputBinding:
            position: 9
    OptArray:
        type:
          - "null"
          - type: array
            items: string
        inputBinding:
            position: 10
    OptEnum:
        type:
          - "null"
          - type: enum
            symbols:
              - vam
              - ram
    inputStringWithoutPosition:
        type: string
        inputBinding:
            prefix: --prefix
  outputs:
    cropped_tif:
        outputBinding:
            glob: .
        type: Directory
        label: Cropped band
        doc: Cropped Sentinel-2 band
    flat_cropped_tif:
         outputBinding:
             glob: .
         type: Directory
         label: Flat Cropped band
         doc: Flat Cropped Sentinel-2 band
    file_output:
        outputBinding:
            glob: ./out
        type: File
        label: File output
        doc: File output of the crop

$namespaces:
    s: https://schema.org/
s:softwareVersion: 1.0.0
$schemas:
- http://schema.org/version/latest/schemaorg-current-http.rdf