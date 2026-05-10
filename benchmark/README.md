# Benchmark

This benchmark measures the average XML parsing time and the classload time for the selected XML parser. The following
parsers were benchmarked:
 * Xerces2 (the default parser)
 * FasterXML/aalto-xml v1.3.3
 * CelerXML v1.0.2

| ---------------:| :------------ |
| Iterations      | 5.000         |
| XML file size   | 1.361 Kb      |
| XML lines count | 42.161        |
| CPU             | i7 / 2.70 GHz |
| RAM             | 8 Gb          |

## StAX XML parsing time

```mermaid
xychart
    title "StAX XML parsing time"
    x-axis [Xerces2, FasterXML, CelerXML]
    y-axis "Time (in us)"
    bar [13113, 12732, 12285]
```

## SAX2 XML parsing time

```mermaid
xychart
    title "SAX2 XML parsing time"
    x-axis [Xerces2, FasterXML, CelerXML]
    y-axis "Time (in us)"
    bar [7784, 7763, 7757]
```

## Classload time

```mermaid
xychart
    title "Classload time"
    x-axis [Xerces2, FasterXML, CelerXML]
    y-axis "Time (in us)"
    bar [14783, 25125, 13566]
```
