# Changelog

## [0.4.0](https://github.com/numaproj-contrib/apache-pulsar-java/compare/v0.3.0...v0.4.0) (2026-04-23)


### Features

* add a working logger sink to kickstart the project ([#1](https://github.com/numaproj-contrib/apache-pulsar-java/issues/1)) ([b3ecf05](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b3ecf053b371f55c1433d9d703ec928571b0a07b))
* add configurations and implement simple source ([#14](https://github.com/numaproj-contrib/apache-pulsar-java/issues/14)) ([362ecd8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/362ecd8cb91d1f9b374e164b6cdcaba33de67288))
* add Pulsar Client and Pulsar Producer Configurations ([#6](https://github.com/numaproj-contrib/apache-pulsar-java/issues/6)) ([664a94b](https://github.com/numaproj-contrib/apache-pulsar-java/commit/664a94bd54661d1643113dd11c728584ba82493f))
* add spring boot to working logger ([#2](https://github.com/numaproj-contrib/apache-pulsar-java/issues/2)) ([783fbba](https://github.com/numaproj-contrib/apache-pulsar-java/commit/783fbba2e201dbe513f22ce99ce47c9b35281c83))
* add working sink that can communicate with local Pulsar  ([#3](https://github.com/numaproj-contrib/apache-pulsar-java/issues/3)) ([85d97b8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/85d97b8c4d59214bbf60d33f2c78ef8998c85647))
* automated cicd ([#79](https://github.com/numaproj-contrib/apache-pulsar-java/issues/79)) ([bba5476](https://github.com/numaproj-contrib/apache-pulsar-java/commit/bba5476f4f7e4b764779882f0b82d2c6aa76bc81))
* batch ack ([#55](https://github.com/numaproj-contrib/apache-pulsar-java/issues/55)) ([cb6fa1a](https://github.com/numaproj-contrib/apache-pulsar-java/commit/cb6fa1a64cb4f4d2165d4e0c76f7c35a102b9037))
* benchmarking cicd ([#84](https://github.com/numaproj-contrib/apache-pulsar-java/issues/84)) ([7330b3c](https://github.com/numaproj-contrib/apache-pulsar-java/commit/7330b3c3a57ec81186349a801ec7e93198f7815c))
* change simple source to be a pulsar udsource ([#15](https://github.com/numaproj-contrib/apache-pulsar-java/issues/15)) ([b3e09d5](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b3e09d5bf27179e71a712884741da0360f8db8a8))
* change to send async messages ([#11](https://github.com/numaproj-contrib/apache-pulsar-java/issues/11)) ([0a2b27b](https://github.com/numaproj-contrib/apache-pulsar-java/commit/0a2b27bddc17b9d305ab632e22ea913ebd33a9cb))
* cicd checkstyle ([#80](https://github.com/numaproj-contrib/apache-pulsar-java/issues/80)) ([19746e9](https://github.com/numaproj-contrib/apache-pulsar-java/commit/19746e954255a552e6e358b9bfac275ddab9c099))
* code coverage  ([#81](https://github.com/numaproj-contrib/apache-pulsar-java/issues/81)) ([651209e](https://github.com/numaproj-contrib/apache-pulsar-java/commit/651209e0bff4efbb64f3e9c057a2f7dd24bd4a68))
* consumer multi topic support ([#58](https://github.com/numaproj-contrib/apache-pulsar-java/issues/58)) ([5c9f9bc](https://github.com/numaproj-contrib/apache-pulsar-java/commit/5c9f9bc0a92e3c5eccf0e0846085067a204a37bd))
* consumer schema validation for avro ([#59](https://github.com/numaproj-contrib/apache-pulsar-java/issues/59)) ([97d782f](https://github.com/numaproj-contrib/apache-pulsar-java/commit/97d782f6991cdeb41be1935ae7716292cea0858f))
* extract message metadata ([#54](https://github.com/numaproj-contrib/apache-pulsar-java/issues/54)) ([f241404](https://github.com/numaproj-contrib/apache-pulsar-java/commit/f241404d04eeaa3034759a3418b9dac6fda8945d))
* graceful shutdown ([#65](https://github.com/numaproj-contrib/apache-pulsar-java/issues/65)) ([7068099](https://github.com/numaproj-contrib/apache-pulsar-java/commit/706809981a75a1f409b04fe66611f55a30e85ef8))
* implement batchReceive and PulsarConsumerManager ([#18](https://github.com/numaproj-contrib/apache-pulsar-java/issues/18)) ([2c06f41](https://github.com/numaproj-contrib/apache-pulsar-java/commit/2c06f418b9ad344b7bd1fb6b46ef91eeeb9f9265))
* introduce pulsar admin config ([#27](https://github.com/numaproj-contrib/apache-pulsar-java/issues/27)) ([12ad541](https://github.com/numaproj-contrib/apache-pulsar-java/commit/12ad5418c9e6523f2c18d6b0dc122e877b723767))
* k8s secret for pulsar api key ([#50](https://github.com/numaproj-contrib/apache-pulsar-java/issues/50)) ([1548340](https://github.com/numaproj-contrib/apache-pulsar-java/commit/1548340e09b6d0b8c16b73b6fd2d6cb85134b47d))
* pending with hardcoded service URL ([#26](https://github.com/numaproj-contrib/apache-pulsar-java/issues/26)) ([697529c](https://github.com/numaproj-contrib/apache-pulsar-java/commit/697529caf4ec37d19e5372fb70d7e31af106ddcc))
* producer avro schema validation ([#60](https://github.com/numaproj-contrib/apache-pulsar-java/issues/60)) ([63ab552](https://github.com/numaproj-contrib/apache-pulsar-java/commit/63ab552a8e6ca634c3f53ff3c23bc4de7ade2ceb))
* rm producer topic creation ([#56](https://github.com/numaproj-contrib/apache-pulsar-java/issues/56)) ([247789f](https://github.com/numaproj-contrib/apache-pulsar-java/commit/247789faae0f2c6cb295a15d11be6b00b541af04))
* rm spring boot ([#63](https://github.com/numaproj-contrib/apache-pulsar-java/issues/63)) ([6f7e427](https://github.com/numaproj-contrib/apache-pulsar-java/commit/6f7e427c8b7f34fdf3dc04d3a23660e9319ab69b))
* structured logging ([#86](https://github.com/numaproj-contrib/apache-pulsar-java/issues/86)) ([11f46e7](https://github.com/numaproj-contrib/apache-pulsar-java/commit/11f46e753fa38553942274006f2a2a1995023bc0))
* support partitioned topics for getPending and getPartitions ([#29](https://github.com/numaproj-contrib/apache-pulsar-java/issues/29)) ([e440f60](https://github.com/numaproj-contrib/apache-pulsar-java/commit/e440f6090efd1bda457fbb33af0ff831776f8a80))
* turn Consumer to be a spring bean ([#16](https://github.com/numaproj-contrib/apache-pulsar-java/issues/16)) ([9395d37](https://github.com/numaproj-contrib/apache-pulsar-java/commit/9395d378b2d641c9e8d5975f9435bd5be194537e))
* use EnvVar and ConfigMap to make pulsar configurable ([#4](https://github.com/numaproj-contrib/apache-pulsar-java/issues/4)) ([c304b3f](https://github.com/numaproj-contrib/apache-pulsar-java/commit/c304b3f616e782805ab588b26615719cf0d7d82a))
* use pulsar client instead of pulsar template ([#5](https://github.com/numaproj-contrib/apache-pulsar-java/issues/5)) ([36b04e9](https://github.com/numaproj-contrib/apache-pulsar-java/commit/36b04e9eddad9fd0af3838bbc75d5771232a7468))


### Bug Fixes

* add back configuration properties (MERGE FIRST) ([#32](https://github.com/numaproj-contrib/apache-pulsar-java/issues/32)) ([1b6344d](https://github.com/numaproj-contrib/apache-pulsar-java/commit/1b6344dd57fec19d89d0096ebf1c4e4b29ab9658))
* add producerName override ([#12](https://github.com/numaproj-contrib/apache-pulsar-java/issues/12)) ([48f6a0c](https://github.com/numaproj-contrib/apache-pulsar-java/commit/48f6a0c924fda0c562ff953b829304c602b354bd))
* change subscription default name ([#31](https://github.com/numaproj-contrib/apache-pulsar-java/issues/31)) ([e75f792](https://github.com/numaproj-contrib/apache-pulsar-java/commit/e75f7921d695807f587868ca5fd8048d60b79853))


### Documentation

* add docs for byte arr consumer ([#30](https://github.com/numaproj-contrib/apache-pulsar-java/issues/30)) ([6cfa90a](https://github.com/numaproj-contrib/apache-pulsar-java/commit/6cfa90aad3f31c9e8bcb056ebe2fad68dd73b75e))
* add docs for byte arr sink ([#13](https://github.com/numaproj-contrib/apache-pulsar-java/issues/13)) ([149a321](https://github.com/numaproj-contrib/apache-pulsar-java/commit/149a321257fc7254565bc64c8fcc59077261c581))
* avro schema validation ([#62](https://github.com/numaproj-contrib/apache-pulsar-java/issues/62)) ([640e6c9](https://github.com/numaproj-contrib/apache-pulsar-java/commit/640e6c92a29eaa1111e18b5c927e603156fd37da))
* docs site setup ([#88](https://github.com/numaproj-contrib/apache-pulsar-java/issues/88)) ([32f0298](https://github.com/numaproj-contrib/apache-pulsar-java/commit/32f02989a020c324c27781ec710ba9aa57cc101b))
* improve instructions for setting pulsar manager UI ([#9](https://github.com/numaproj-contrib/apache-pulsar-java/issues/9)) ([7c49b84](https://github.com/numaproj-contrib/apache-pulsar-java/commit/7c49b84e5dc8685e06f5e3b5d95cf20847a787e2))
* improve local setup documentation ([#49](https://github.com/numaproj-contrib/apache-pulsar-java/issues/49)) ([ba19ac8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/ba19ac83dcba33c6c85b19dbed0ac658bf68fe5f))
* performance testing ([#64](https://github.com/numaproj-contrib/apache-pulsar-java/issues/64)) ([d3345ef](https://github.com/numaproj-contrib/apache-pulsar-java/commit/d3345ef727cbd38a67410bfc41101b099f72f060))
* schema validation testing scripts ([#61](https://github.com/numaproj-contrib/apache-pulsar-java/issues/61)) ([b279b08](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b279b08c74b0195d380dcf9f6937bb4ced67a8af))
* updated docs to work with jwt token auth ([#33](https://github.com/numaproj-contrib/apache-pulsar-java/issues/33)) ([c34e977](https://github.com/numaproj-contrib/apache-pulsar-java/commit/c34e9771c66aa6c7f37e72f62738871655ba0cd5))
* user guide ([#36](https://github.com/numaproj-contrib/apache-pulsar-java/issues/36)) ([4cbaa28](https://github.com/numaproj-contrib/apache-pulsar-java/commit/4cbaa285e0534cdb085812cb5a4842b15c12fe88))
