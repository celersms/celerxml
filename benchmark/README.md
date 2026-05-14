# Benchmark

This benchmark measures the average XML parsing time and the classload time for the selected XML parser. The following
parsers were benchmarked:
 * *Xerces2* (the default parser)
 * *FasterXML/aalto-xml* v1.3.3
 * *CelerXML* v1.0.2

The following table summarizes the benchmark settings and environment:

|                 |               |
| ---------------:| :------------ |
| Iterations      | 5.000         |
| XML file size   | 1.361 Kb      |
| XML lines count | 42.161        |
| CPU             | i7 / 2.70 GHz |
| RAM             | 8 Gb          |

## Sample XML files for the benchmark

Several sample XML files are included:

| XML file                                       | Description                 |
| ----------------------------------------------:| :-------------------------- |
| [excel_test.xml](files/excel_test.xml)         | Simple Excel test file      |
| [large_xml_file.xml](files/large_xml_file.xml) | Mystic library              |
| [sheet1.xml](files/sheet1.xml)                 | OpenXML Spreadsheet         |
| [test-opc.xml](files/test-opc.xml)             | Sample OPC Data Access file |
| [test.xml](files/test.xml)                     | Simple XML file             |

This report was generated using the Mystic library as the input XML file.

## Benchmark report

The following charts represent the performance indicators measured when benchmarking the XML parsers.

```mermaid
---
config:
  xyChart:
    height: 250
    showDataLabel: true
---
xychart
    title "StAX XML parsing time (in µs)"
    x-axis [Xerces2, FasterXML, CelerXML]
    y-axis Time 10000 --> 15000
    bar [14958, 11083, 10468]
```

```mermaid
---
config:
  xyChart:
    height: 250
    showDataLabel: true
---
xychart
    title "SAX2 XML parsing time (in µs)"
    x-axis [Xerces2, FasterXML, CelerXML]
    y-axis Time 10000 --> 20000
    bar [18879, 14577, 13677]
```

```mermaid
---
config:
  xyChart:
    height: 250
    showDataLabel: true
---
xychart
    title "Classload time (in µs)"
    x-axis [Xerces2, FasterXML, CelerXML]
    y-axis Time 10000 --> 25200
    bar [14783, 25125, 13566]
```
