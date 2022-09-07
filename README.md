[![version](https://img.shields.io/github/license/texttechnologylab/WikiDragon)]()
[![](https://jitpack.io/v/texttechnologylab/WikiDragon.svg)](https://jitpack.io/#texttechnologylab/WikiDragon)

[![Paper](http://img.shields.io/badge/paper-ACL--anthology-B31B1B.svg)](https://aclanthology.org/L18-1589.pdf)
[![Conference](http://img.shields.io/badge/conference-LREC--2018-4b44ce.svg)](http://lrec2018.lrec-conf.org/en/)

# WikiDragon
A Java Framework to build and work on MediaWikis offline.

See https://github.com/texttechnologylab/WikiDragon/wiki to get started.

## Abstract
We introduce WikiDragon, a Java Framework designed to give developers in computational linguistics an intuitive API to build, parse and analyze instances of MediaWikis such as Wikipedia, Wiktionary or WikiSource on their computers. It covers current versions of pages as well as the complete revision history, gives diachronic access to both page source code as well as accurately parsed HTML and supports the diachronic exploration of the page network. WikiDragon is self-enclosed and only requires an XML dump of the official Wikimedia Foundation website for import into an embedded database. No additional setup is required. We describe WikiDragon’s architecture and evaluate the framework based on the simple English Wikipedia with respect to the accuracy of link extraction, diachronic network analysis and the impact of using different Wikipedia frameworks to text analysis.

# Cite
When using WikiDragon please cite

R. Gleim, A. Mehler, and S. Y. Song, “WikiDragon: A Java Framework For Diachronic Content And Network Analysis Of MediaWikis,” in Proceedings of the 11th edition of the Language Resources and Evaluation Conference, May 7 – 12, Miyazaki, Japan, 2018. ![[Link]](https://aclanthology.org/L18-1589.pdf)

```
@inproceedings{Gleim:Mehler:Song:2018,
    author    = {R{\"u}diger Gleim and Alexander Mehler and Sung Y. Song},
    title     = {WikiDragon: A Java Framework For Diachronic Content And Network Analysis Of MediaWikis},
    booktitle = {Proceedings of the 11th edition of the Language Resources and Evaluation Conference, May 7 - 12},
    address = {Miyazaki, Japan},
    year      = {2018},
    series    = {LREC 2018}
}
```
