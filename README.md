Prometheus Apache Knox Exporter
=======

[![Maven Central](https://img.shields.io/maven-central/v/de.m3y.prometheus.exporter.knox/knox-exporter.svg?style=flat-square)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22de.m3y.prometheus.exporter.knox%22%20AND%20a%3A%22knox-exporter%22)

A black box [Apache Knox](http://knox.apache.org) exporter for [Prometheus](https://prometheus.io/) supporting
* WebHDFS status 
    
The exporter collects 
* error count 
* duration summary with 0.5/0.95/0.99 quantiles

## Requirements
For building:
* JDK 8
* [Maven 3.5.x](http://maven.apache.org)

For running:
* JRE 8 for running

## Downloading

Available on [![Maven Central](https://img.shields.io/maven-central/v/de.m3y.prometheus.exporter.knox/knox-exporter.svg?style=flat-square)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22de.m3y.prometheus.exporter.knox%22%20AND%20a%3A%22knox-exporter%22)

## Building

```mvn clean install```

## Installation and configuration

* Download JAR from [![Maven Central](https://img.shields.io/maven-central/v/de.m3y.prometheus.exporter.knox/knox-exporter.svg?style=flat-square)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22de.m3y.prometheus.exporter.knox%22%20AND%20a%3A%22knox-exporter%22)

* Configure the exporter     
  Create a yml file (see [example.yml](example.yml)):
  ```
  # User for connecting to Knox
  username : 'foo'
  # Password for authenticating user
  password : '***'
  
  # WebHDFS STATUS call directory or file path
  webHdfStatusPath : '/'
  ```
 
* Run the exporter
  ```
    > java -jar knox-exporter-<VERSION>.jar
    Usage: WebServer <hostname> <port> <knox gateway url> <yml configuration file>
  ```
  Example including JVM opts
  ```
  > java -Xmx256m -server \
         -jar knox-exporter-1.0-SNAPSHOT.jar \
         0.0.0.0 9092 http://localhost:8080/gateway/default example.yml
  ```
  
* Test the exporter  
  Open http://\<hostname>:\<port>/metrics or http://\<hostname>:\<port>/ (for configuration overview)
   
* Add to prometheus
  ```
  - job_name: 'knox'
      scrape_interval: 30s
      scrape_timeout:  20s
      static_configs:
        - targets: ['<exporter hostname>:<exporter port>']
          labels:
            ...
  ```

## Roadmap

See [issues](../../issues)

## Example output

### Example home output

![Home output](home.png)

### Example metrics
Here's an example output:

```
# HELP knox_exporter_scrape_duration_seconds Scrape duration
# TYPE knox_exporter_scrape_duration_seconds gauge
knox_exporter_scrape_duration_seconds 0.009847488
# HELP knox_exporter_app_info Application build info
# TYPE knox_exporter_app_info gauge
knox_exporter_app_info{appName="knox_exporter",appVersion="<will be replaced>",buildTime="<will be replaced>",buildScmVersion="<will be replaced>",buildScmBranch="<will be replaced>",} 1.0
# HELP knox_exporter_scrape_errors_total Counts failed scrapes.
# TYPE knox_exporter_scrape_errors_total counter
knox_exporter_scrape_errors_total 0.0
# HELP knox_exporter_scrape_requests_total Exporter requests made
# TYPE knox_exporter_scrape_requests_total counter
knox_exporter_scrape_requests_total 2.0
# HELP knox_exporter_ops_errors_total Counts errors.
# TYPE knox_exporter_ops_errors_total counter
knox_exporter_ops_errors_total{action="webhdfs_status",} 2.0
# HELP knox_exporter_ops_duration_seconds Ops duration
# TYPE knox_exporter_ops_duration_seconds summary
knox_exporter_ops_duration_seconds{action="webhdfs_status",quantile="0.5",} 0.00328685
knox_exporter_ops_duration_seconds{action="webhdfs_status",quantile="0.95",} 0.00328685
knox_exporter_ops_duration_seconds{action="webhdfs_status",quantile="0.99",} 0.00328685
knox_exporter_ops_duration_seconds_count{action="webhdfs_status",} 2.0
knox_exporter_ops_duration_seconds_sum{action="webhdfs_status",} 0.08379816
# HELP jvm_memory_bytes_used Used bytes of a given JVM memory area.
# TYPE jvm_memory_bytes_used gauge
jvm_memory_bytes_used{area="heap",} 2.4533552E7
jvm_memory_bytes_used{area="nonheap",} 2.4157928E7
# HELP jvm_memory_bytes_committed Committed (bytes) of a given JVM memory area.
# TYPE jvm_memory_bytes_committed gauge
jvm_memory_bytes_committed{area="heap",} 1.28974848E8
jvm_memory_bytes_committed{area="nonheap",} 2.4969216E7
# HELP jvm_memory_bytes_max Max (bytes) of a given JVM memory area.
# TYPE jvm_memory_bytes_max gauge
jvm_memory_bytes_max{area="heap",} 1.908932608E9
jvm_memory_bytes_max{area="nonheap",} -1.0
# HELP jvm_memory_bytes_init Initial bytes of a given JVM memory area.
# TYPE jvm_memory_bytes_init gauge
jvm_memory_bytes_init{area="heap",} 1.34217728E8
jvm_memory_bytes_init{area="nonheap",} 2555904.0
# HELP jvm_memory_pool_bytes_used Used bytes of a given JVM memory pool.
# TYPE jvm_memory_pool_bytes_used gauge
jvm_memory_pool_bytes_used{pool="Code Cache",} 4754816.0
jvm_memory_pool_bytes_used{pool="Metaspace",} 1.7353064E7
jvm_memory_pool_bytes_used{pool="Compressed Class Space",} 2050048.0
jvm_memory_pool_bytes_used{pool="PS Eden Space",} 1.749076E7
jvm_memory_pool_bytes_used{pool="PS Survivor Space",} 5229664.0
jvm_memory_pool_bytes_used{pool="PS Old Gen",} 1813128.0
# HELP jvm_memory_pool_bytes_committed Committed bytes of a given JVM memory pool.
# TYPE jvm_memory_pool_bytes_committed gauge
jvm_memory_pool_bytes_committed{pool="Code Cache",} 4784128.0
jvm_memory_pool_bytes_committed{pool="Metaspace",} 1.7956864E7
jvm_memory_pool_bytes_committed{pool="Compressed Class Space",} 2228224.0
jvm_memory_pool_bytes_committed{pool="PS Eden Space",} 3.407872E7
jvm_memory_pool_bytes_committed{pool="PS Survivor Space",} 5242880.0
jvm_memory_pool_bytes_committed{pool="PS Old Gen",} 8.9653248E7
# HELP jvm_memory_pool_bytes_max Max bytes of a given JVM memory pool.
# TYPE jvm_memory_pool_bytes_max gauge
jvm_memory_pool_bytes_max{pool="Code Cache",} 2.5165824E8
jvm_memory_pool_bytes_max{pool="Metaspace",} -1.0
jvm_memory_pool_bytes_max{pool="Compressed Class Space",} 1.073741824E9
jvm_memory_pool_bytes_max{pool="PS Eden Space",} 7.0516736E8
jvm_memory_pool_bytes_max{pool="PS Survivor Space",} 5242880.0
jvm_memory_pool_bytes_max{pool="PS Old Gen",} 1.431830528E9
# HELP jvm_memory_pool_bytes_init Initial bytes of a given JVM memory pool.
# TYPE jvm_memory_pool_bytes_init gauge
jvm_memory_pool_bytes_init{pool="Code Cache",} 2555904.0
jvm_memory_pool_bytes_init{pool="Metaspace",} 0.0
jvm_memory_pool_bytes_init{pool="Compressed Class Space",} 0.0
jvm_memory_pool_bytes_init{pool="PS Eden Space",} 3.407872E7
jvm_memory_pool_bytes_init{pool="PS Survivor Space",} 5242880.0
jvm_memory_pool_bytes_init{pool="PS Old Gen",} 8.9653248E7
```
## License

The Knox Exporter is released under the [Apache 2.0 license](LICENSE).

```
Copyright 2018 Marcel May

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
