# Changelog

## [0.5.2](https://github.com/numaproj-contrib/apache-pulsar-java/compare/v0.5.1...v0.5.2) (2026-04-01)


### Bug Fixes

* test run ([020fd76](https://github.com/numaproj-contrib/apache-pulsar-java/commit/020fd76aa9691d1cd590e65bc50def89349adc04))
* use release please tag ([dd621a9](https://github.com/numaproj-contrib/apache-pulsar-java/commit/dd621a975aa10fdd690abc8b5db38a4d860da243))

## [0.5.1](https://github.com/numaproj-contrib/apache-pulsar-java/compare/v0.5.0...v0.5.1) (2026-04-01)


### Bug Fixes

* try accessing tag thru release please stage ([bdffcc1](https://github.com/numaproj-contrib/apache-pulsar-java/commit/bdffcc1d1ed60355cbce3a5dee6d3cce59075985))

## [0.5.0](https://github.com/numaproj-contrib/apache-pulsar-java/compare/v0.4.2...v0.5.0) (2026-04-01)


### Features

* test run 2 ([cfd36e3](https://github.com/numaproj-contrib/apache-pulsar-java/commit/cfd36e31670f33cd6b1ba8d29b02600f7b94ec0b))

## [0.4.2](https://github.com/numaproj-contrib/apache-pulsar-java/compare/v0.4.1...v0.4.2) (2026-04-01)


### Bug Fixes

* remove release lock ([688ba99](https://github.com/numaproj-contrib/apache-pulsar-java/commit/688ba99738ec6a6580f9c31ce70f9b7f52507ed4))
* test run 1 ([60fb63c](https://github.com/numaproj-contrib/apache-pulsar-java/commit/60fb63c1c0c9717c3364b902df36bf374cdff9c5))

## [0.4.1](https://github.com/numaproj-contrib/apache-pulsar-java/compare/v0.4.1...v0.4.1) (2026-04-01)


### Features

* add a working logger sink to kickstart the project ([#1](https://github.com/numaproj-contrib/apache-pulsar-java/issues/1)) ([b3ecf05](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b3ecf053b371f55c1433d9d703ec928571b0a07b))
* add configurations and implement simple source ([#14](https://github.com/numaproj-contrib/apache-pulsar-java/issues/14)) ([362ecd8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/362ecd8cb91d1f9b374e164b6cdcaba33de67288))
* add Pulsar Client and Pulsar Producer Configurations ([#6](https://github.com/numaproj-contrib/apache-pulsar-java/issues/6)) ([664a94b](https://github.com/numaproj-contrib/apache-pulsar-java/commit/664a94bd54661d1643113dd11c728584ba82493f))
* add spring boot to working logger ([#2](https://github.com/numaproj-contrib/apache-pulsar-java/issues/2)) ([783fbba](https://github.com/numaproj-contrib/apache-pulsar-java/commit/783fbba2e201dbe513f22ce99ce47c9b35281c83))
* add working sink that can communicate with local Pulsar  ([#3](https://github.com/numaproj-contrib/apache-pulsar-java/issues/3)) ([85d97b8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/85d97b8c4d59214bbf60d33f2c78ef8998c85647))
* batch ack ([#55](https://github.com/numaproj-contrib/apache-pulsar-java/issues/55)) ([cb6fa1a](https://github.com/numaproj-contrib/apache-pulsar-java/commit/cb6fa1a64cb4f4d2165d4e0c76f7c35a102b9037))
* change simple source to be a pulsar udsource ([#15](https://github.com/numaproj-contrib/apache-pulsar-java/issues/15)) ([b3e09d5](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b3e09d5bf27179e71a712884741da0360f8db8a8))
* change to send async messages ([#11](https://github.com/numaproj-contrib/apache-pulsar-java/issues/11)) ([0a2b27b](https://github.com/numaproj-contrib/apache-pulsar-java/commit/0a2b27bddc17b9d305ab632e22ea913ebd33a9cb))
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
* support partitioned topics for getPending and getPartitions ([#29](https://github.com/numaproj-contrib/apache-pulsar-java/issues/29)) ([e440f60](https://github.com/numaproj-contrib/apache-pulsar-java/commit/e440f6090efd1bda457fbb33af0ff831776f8a80))
* turn Consumer to be a spring bean ([#16](https://github.com/numaproj-contrib/apache-pulsar-java/issues/16)) ([9395d37](https://github.com/numaproj-contrib/apache-pulsar-java/commit/9395d378b2d641c9e8d5975f9435bd5be194537e))
* use EnvVar and ConfigMap to make pulsar configurable ([#4](https://github.com/numaproj-contrib/apache-pulsar-java/issues/4)) ([c304b3f](https://github.com/numaproj-contrib/apache-pulsar-java/commit/c304b3f616e782805ab588b26615719cf0d7d82a))
* use pulsar client instead of pulsar template ([#5](https://github.com/numaproj-contrib/apache-pulsar-java/issues/5)) ([36b04e9](https://github.com/numaproj-contrib/apache-pulsar-java/commit/36b04e9eddad9fd0af3838bbc75d5771232a7468))


### Bug Fixes

* add back configuration properties (MERGE FIRST) ([#32](https://github.com/numaproj-contrib/apache-pulsar-java/issues/32)) ([1b6344d](https://github.com/numaproj-contrib/apache-pulsar-java/commit/1b6344dd57fec19d89d0096ebf1c4e4b29ab9658))
* add producerName override ([#12](https://github.com/numaproj-contrib/apache-pulsar-java/issues/12)) ([48f6a0c](https://github.com/numaproj-contrib/apache-pulsar-java/commit/48f6a0c924fda0c562ff953b829304c602b354bd))
* back to manifest to avoid snapshot ([b43597f](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b43597f8755d8589bf82da738ed578857264864c))
* change subscription default name ([#31](https://github.com/numaproj-contrib/apache-pulsar-java/issues/31)) ([e75f792](https://github.com/numaproj-contrib/apache-pulsar-java/commit/e75f7921d695807f587868ca5fd8048d60b79853))
* change title pattern ([bfd9d89](https://github.com/numaproj-contrib/apache-pulsar-java/commit/bfd9d8936c0ddd0e1f9c78ff43963648353c2a59))
* edited image tagging ([fb1e414](https://github.com/numaproj-contrib/apache-pulsar-java/commit/fb1e4143436faa6161aa0eaca93a119d33e45e85))
* fixed wrong parameters ([c7fadbb](https://github.com/numaproj-contrib/apache-pulsar-java/commit/c7fadbb0d6f554f09c8342addcdb61bb5d2810d6))
* image tag ([119ad23](https://github.com/numaproj-contrib/apache-pulsar-java/commit/119ad235f8af2caa1e2afddc6cf23dda3711fd07))
* lock release as ([9516160](https://github.com/numaproj-contrib/apache-pulsar-java/commit/9516160b15ea852f9d76a617363b40a5bf59133c))
* release-please simple mode ([834fc19](https://github.com/numaproj-contrib/apache-pulsar-java/commit/834fc1998fec7301b059a19545cd6f126867e802))
* rm unnecessary field ([2259cd2](https://github.com/numaproj-contrib/apache-pulsar-java/commit/2259cd23befa545752576637efc56348b9fee497))
* test run ([15d81b8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/15d81b8e877a31d7489fcd5a208090bf54305a7a))
* test run edit ([fcf450f](https://github.com/numaproj-contrib/apache-pulsar-java/commit/fcf450f7ba2e1433fffa1deae227c4601820d70b))
* testing release-please ([d544649](https://github.com/numaproj-contrib/apache-pulsar-java/commit/d5446493c956caa666a133e073fafed49ebdddb9))
* ues pat for release please pr permission ([ccaf5b1](https://github.com/numaproj-contrib/apache-pulsar-java/commit/ccaf5b16b944f9bb6efef83b9a721816acf11be3))


### Documentation

* add docs for byte arr consumer ([#30](https://github.com/numaproj-contrib/apache-pulsar-java/issues/30)) ([6cfa90a](https://github.com/numaproj-contrib/apache-pulsar-java/commit/6cfa90aad3f31c9e8bcb056ebe2fad68dd73b75e))
* add docs for byte arr sink ([#13](https://github.com/numaproj-contrib/apache-pulsar-java/issues/13)) ([149a321](https://github.com/numaproj-contrib/apache-pulsar-java/commit/149a321257fc7254565bc64c8fcc59077261c581))
* avro schema validation ([#62](https://github.com/numaproj-contrib/apache-pulsar-java/issues/62)) ([640e6c9](https://github.com/numaproj-contrib/apache-pulsar-java/commit/640e6c92a29eaa1111e18b5c927e603156fd37da))
* improve instructions for setting pulsar manager UI ([#9](https://github.com/numaproj-contrib/apache-pulsar-java/issues/9)) ([7c49b84](https://github.com/numaproj-contrib/apache-pulsar-java/commit/7c49b84e5dc8685e06f5e3b5d95cf20847a787e2))
* improve local setup documentation ([#49](https://github.com/numaproj-contrib/apache-pulsar-java/issues/49)) ([ba19ac8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/ba19ac83dcba33c6c85b19dbed0ac658bf68fe5f))
* schema validation testing scripts ([#61](https://github.com/numaproj-contrib/apache-pulsar-java/issues/61)) ([b279b08](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b279b08c74b0195d380dcf9f6937bb4ced67a8af))
* updated docs to work with jwt token auth ([#33](https://github.com/numaproj-contrib/apache-pulsar-java/issues/33)) ([c34e977](https://github.com/numaproj-contrib/apache-pulsar-java/commit/c34e9771c66aa6c7f37e72f62738871655ba0cd5))
* user guide ([#36](https://github.com/numaproj-contrib/apache-pulsar-java/issues/36)) ([4cbaa28](https://github.com/numaproj-contrib/apache-pulsar-java/commit/4cbaa285e0534cdb085812cb5a4842b15c12fe88))

## [0.4.1](https://github.com/numaproj-contrib/apache-pulsar-java/compare/v0.4.1...v0.4.1) (2026-04-01)


### Bug Fixes

* image tag ([119ad23](https://github.com/numaproj-contrib/apache-pulsar-java/commit/119ad235f8af2caa1e2afddc6cf23dda3711fd07))

## [0.4.1](https://github.com/numaproj-contrib/apache-pulsar-java/compare/v0.4.1...v0.4.1) (2026-04-01)


### Features

* add a working logger sink to kickstart the project ([#1](https://github.com/numaproj-contrib/apache-pulsar-java/issues/1)) ([b3ecf05](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b3ecf053b371f55c1433d9d703ec928571b0a07b))
* add configurations and implement simple source ([#14](https://github.com/numaproj-contrib/apache-pulsar-java/issues/14)) ([362ecd8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/362ecd8cb91d1f9b374e164b6cdcaba33de67288))
* add Pulsar Client and Pulsar Producer Configurations ([#6](https://github.com/numaproj-contrib/apache-pulsar-java/issues/6)) ([664a94b](https://github.com/numaproj-contrib/apache-pulsar-java/commit/664a94bd54661d1643113dd11c728584ba82493f))
* add spring boot to working logger ([#2](https://github.com/numaproj-contrib/apache-pulsar-java/issues/2)) ([783fbba](https://github.com/numaproj-contrib/apache-pulsar-java/commit/783fbba2e201dbe513f22ce99ce47c9b35281c83))
* add working sink that can communicate with local Pulsar  ([#3](https://github.com/numaproj-contrib/apache-pulsar-java/issues/3)) ([85d97b8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/85d97b8c4d59214bbf60d33f2c78ef8998c85647))
* batch ack ([#55](https://github.com/numaproj-contrib/apache-pulsar-java/issues/55)) ([cb6fa1a](https://github.com/numaproj-contrib/apache-pulsar-java/commit/cb6fa1a64cb4f4d2165d4e0c76f7c35a102b9037))
* change simple source to be a pulsar udsource ([#15](https://github.com/numaproj-contrib/apache-pulsar-java/issues/15)) ([b3e09d5](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b3e09d5bf27179e71a712884741da0360f8db8a8))
* change to send async messages ([#11](https://github.com/numaproj-contrib/apache-pulsar-java/issues/11)) ([0a2b27b](https://github.com/numaproj-contrib/apache-pulsar-java/commit/0a2b27bddc17b9d305ab632e22ea913ebd33a9cb))
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
* support partitioned topics for getPending and getPartitions ([#29](https://github.com/numaproj-contrib/apache-pulsar-java/issues/29)) ([e440f60](https://github.com/numaproj-contrib/apache-pulsar-java/commit/e440f6090efd1bda457fbb33af0ff831776f8a80))
* turn Consumer to be a spring bean ([#16](https://github.com/numaproj-contrib/apache-pulsar-java/issues/16)) ([9395d37](https://github.com/numaproj-contrib/apache-pulsar-java/commit/9395d378b2d641c9e8d5975f9435bd5be194537e))
* use EnvVar and ConfigMap to make pulsar configurable ([#4](https://github.com/numaproj-contrib/apache-pulsar-java/issues/4)) ([c304b3f](https://github.com/numaproj-contrib/apache-pulsar-java/commit/c304b3f616e782805ab588b26615719cf0d7d82a))
* use pulsar client instead of pulsar template ([#5](https://github.com/numaproj-contrib/apache-pulsar-java/issues/5)) ([36b04e9](https://github.com/numaproj-contrib/apache-pulsar-java/commit/36b04e9eddad9fd0af3838bbc75d5771232a7468))


### Bug Fixes

* add back configuration properties (MERGE FIRST) ([#32](https://github.com/numaproj-contrib/apache-pulsar-java/issues/32)) ([1b6344d](https://github.com/numaproj-contrib/apache-pulsar-java/commit/1b6344dd57fec19d89d0096ebf1c4e4b29ab9658))
* add producerName override ([#12](https://github.com/numaproj-contrib/apache-pulsar-java/issues/12)) ([48f6a0c](https://github.com/numaproj-contrib/apache-pulsar-java/commit/48f6a0c924fda0c562ff953b829304c602b354bd))
* back to manifest to avoid snapshot ([b43597f](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b43597f8755d8589bf82da738ed578857264864c))
* change subscription default name ([#31](https://github.com/numaproj-contrib/apache-pulsar-java/issues/31)) ([e75f792](https://github.com/numaproj-contrib/apache-pulsar-java/commit/e75f7921d695807f587868ca5fd8048d60b79853))
* edited image tagging ([fb1e414](https://github.com/numaproj-contrib/apache-pulsar-java/commit/fb1e4143436faa6161aa0eaca93a119d33e45e85))
* fixed wrong parameters ([c7fadbb](https://github.com/numaproj-contrib/apache-pulsar-java/commit/c7fadbb0d6f554f09c8342addcdb61bb5d2810d6))
* lock release as ([9516160](https://github.com/numaproj-contrib/apache-pulsar-java/commit/9516160b15ea852f9d76a617363b40a5bf59133c))
* release-please simple mode ([834fc19](https://github.com/numaproj-contrib/apache-pulsar-java/commit/834fc1998fec7301b059a19545cd6f126867e802))
* rm unnecessary field ([2259cd2](https://github.com/numaproj-contrib/apache-pulsar-java/commit/2259cd23befa545752576637efc56348b9fee497))
* test run ([15d81b8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/15d81b8e877a31d7489fcd5a208090bf54305a7a))
* test run edit ([fcf450f](https://github.com/numaproj-contrib/apache-pulsar-java/commit/fcf450f7ba2e1433fffa1deae227c4601820d70b))
* testing release-please ([d544649](https://github.com/numaproj-contrib/apache-pulsar-java/commit/d5446493c956caa666a133e073fafed49ebdddb9))
* ues pat for release please pr permission ([ccaf5b1](https://github.com/numaproj-contrib/apache-pulsar-java/commit/ccaf5b16b944f9bb6efef83b9a721816acf11be3))


### Documentation

* add docs for byte arr consumer ([#30](https://github.com/numaproj-contrib/apache-pulsar-java/issues/30)) ([6cfa90a](https://github.com/numaproj-contrib/apache-pulsar-java/commit/6cfa90aad3f31c9e8bcb056ebe2fad68dd73b75e))
* add docs for byte arr sink ([#13](https://github.com/numaproj-contrib/apache-pulsar-java/issues/13)) ([149a321](https://github.com/numaproj-contrib/apache-pulsar-java/commit/149a321257fc7254565bc64c8fcc59077261c581))
* avro schema validation ([#62](https://github.com/numaproj-contrib/apache-pulsar-java/issues/62)) ([640e6c9](https://github.com/numaproj-contrib/apache-pulsar-java/commit/640e6c92a29eaa1111e18b5c927e603156fd37da))
* improve instructions for setting pulsar manager UI ([#9](https://github.com/numaproj-contrib/apache-pulsar-java/issues/9)) ([7c49b84](https://github.com/numaproj-contrib/apache-pulsar-java/commit/7c49b84e5dc8685e06f5e3b5d95cf20847a787e2))
* improve local setup documentation ([#49](https://github.com/numaproj-contrib/apache-pulsar-java/issues/49)) ([ba19ac8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/ba19ac83dcba33c6c85b19dbed0ac658bf68fe5f))
* schema validation testing scripts ([#61](https://github.com/numaproj-contrib/apache-pulsar-java/issues/61)) ([b279b08](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b279b08c74b0195d380dcf9f6937bb4ced67a8af))
* updated docs to work with jwt token auth ([#33](https://github.com/numaproj-contrib/apache-pulsar-java/issues/33)) ([c34e977](https://github.com/numaproj-contrib/apache-pulsar-java/commit/c34e9771c66aa6c7f37e72f62738871655ba0cd5))
* user guide ([#36](https://github.com/numaproj-contrib/apache-pulsar-java/issues/36)) ([4cbaa28](https://github.com/numaproj-contrib/apache-pulsar-java/commit/4cbaa285e0534cdb085812cb5a4842b15c12fe88))

## [0.4.1](https://github.com/numaproj-contrib/apache-pulsar-java/compare/v0.4.0...v0.4.1) (2026-04-01)


### Bug Fixes

* back to manifest to avoid snapshot ([b43597f](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b43597f8755d8589bf82da738ed578857264864c))
* fixed wrong parameters ([c7fadbb](https://github.com/numaproj-contrib/apache-pulsar-java/commit/c7fadbb0d6f554f09c8342addcdb61bb5d2810d6))
* release-please simple mode ([834fc19](https://github.com/numaproj-contrib/apache-pulsar-java/commit/834fc1998fec7301b059a19545cd6f126867e802))
* rm unnecessary field ([2259cd2](https://github.com/numaproj-contrib/apache-pulsar-java/commit/2259cd23befa545752576637efc56348b9fee497))
* test run ([15d81b8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/15d81b8e877a31d7489fcd5a208090bf54305a7a))

## [0.4.0](https://github.com/numaproj-contrib/apache-pulsar-java/compare/v0.3.0...v0.4.0) (2026-03-31)


### Features

* add a working logger sink to kickstart the project ([#1](https://github.com/numaproj-contrib/apache-pulsar-java/issues/1)) ([b3ecf05](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b3ecf053b371f55c1433d9d703ec928571b0a07b))
* add configurations and implement simple source ([#14](https://github.com/numaproj-contrib/apache-pulsar-java/issues/14)) ([362ecd8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/362ecd8cb91d1f9b374e164b6cdcaba33de67288))
* add Pulsar Client and Pulsar Producer Configurations ([#6](https://github.com/numaproj-contrib/apache-pulsar-java/issues/6)) ([664a94b](https://github.com/numaproj-contrib/apache-pulsar-java/commit/664a94bd54661d1643113dd11c728584ba82493f))
* add spring boot to working logger ([#2](https://github.com/numaproj-contrib/apache-pulsar-java/issues/2)) ([783fbba](https://github.com/numaproj-contrib/apache-pulsar-java/commit/783fbba2e201dbe513f22ce99ce47c9b35281c83))
* add working sink that can communicate with local Pulsar  ([#3](https://github.com/numaproj-contrib/apache-pulsar-java/issues/3)) ([85d97b8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/85d97b8c4d59214bbf60d33f2c78ef8998c85647))
* batch ack ([#55](https://github.com/numaproj-contrib/apache-pulsar-java/issues/55)) ([cb6fa1a](https://github.com/numaproj-contrib/apache-pulsar-java/commit/cb6fa1a64cb4f4d2165d4e0c76f7c35a102b9037))
* change simple source to be a pulsar udsource ([#15](https://github.com/numaproj-contrib/apache-pulsar-java/issues/15)) ([b3e09d5](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b3e09d5bf27179e71a712884741da0360f8db8a8))
* change to send async messages ([#11](https://github.com/numaproj-contrib/apache-pulsar-java/issues/11)) ([0a2b27b](https://github.com/numaproj-contrib/apache-pulsar-java/commit/0a2b27bddc17b9d305ab632e22ea913ebd33a9cb))
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
* support partitioned topics for getPending and getPartitions ([#29](https://github.com/numaproj-contrib/apache-pulsar-java/issues/29)) ([e440f60](https://github.com/numaproj-contrib/apache-pulsar-java/commit/e440f6090efd1bda457fbb33af0ff831776f8a80))
* turn Consumer to be a spring bean ([#16](https://github.com/numaproj-contrib/apache-pulsar-java/issues/16)) ([9395d37](https://github.com/numaproj-contrib/apache-pulsar-java/commit/9395d378b2d641c9e8d5975f9435bd5be194537e))
* use EnvVar and ConfigMap to make pulsar configurable ([#4](https://github.com/numaproj-contrib/apache-pulsar-java/issues/4)) ([c304b3f](https://github.com/numaproj-contrib/apache-pulsar-java/commit/c304b3f616e782805ab588b26615719cf0d7d82a))
* use pulsar client instead of pulsar template ([#5](https://github.com/numaproj-contrib/apache-pulsar-java/issues/5)) ([36b04e9](https://github.com/numaproj-contrib/apache-pulsar-java/commit/36b04e9eddad9fd0af3838bbc75d5771232a7468))


### Bug Fixes

* add back configuration properties (MERGE FIRST) ([#32](https://github.com/numaproj-contrib/apache-pulsar-java/issues/32)) ([1b6344d](https://github.com/numaproj-contrib/apache-pulsar-java/commit/1b6344dd57fec19d89d0096ebf1c4e4b29ab9658))
* add producerName override ([#12](https://github.com/numaproj-contrib/apache-pulsar-java/issues/12)) ([48f6a0c](https://github.com/numaproj-contrib/apache-pulsar-java/commit/48f6a0c924fda0c562ff953b829304c602b354bd))
* change subscription default name ([#31](https://github.com/numaproj-contrib/apache-pulsar-java/issues/31)) ([e75f792](https://github.com/numaproj-contrib/apache-pulsar-java/commit/e75f7921d695807f587868ca5fd8048d60b79853))
* edited image tagging ([fb1e414](https://github.com/numaproj-contrib/apache-pulsar-java/commit/fb1e4143436faa6161aa0eaca93a119d33e45e85))
* test run edit ([fcf450f](https://github.com/numaproj-contrib/apache-pulsar-java/commit/fcf450f7ba2e1433fffa1deae227c4601820d70b))
* testing release-please ([d544649](https://github.com/numaproj-contrib/apache-pulsar-java/commit/d5446493c956caa666a133e073fafed49ebdddb9))
* ues pat for release please pr permission ([ccaf5b1](https://github.com/numaproj-contrib/apache-pulsar-java/commit/ccaf5b16b944f9bb6efef83b9a721816acf11be3))


### Documentation

* add docs for byte arr consumer ([#30](https://github.com/numaproj-contrib/apache-pulsar-java/issues/30)) ([6cfa90a](https://github.com/numaproj-contrib/apache-pulsar-java/commit/6cfa90aad3f31c9e8bcb056ebe2fad68dd73b75e))
* add docs for byte arr sink ([#13](https://github.com/numaproj-contrib/apache-pulsar-java/issues/13)) ([149a321](https://github.com/numaproj-contrib/apache-pulsar-java/commit/149a321257fc7254565bc64c8fcc59077261c581))
* avro schema validation ([#62](https://github.com/numaproj-contrib/apache-pulsar-java/issues/62)) ([640e6c9](https://github.com/numaproj-contrib/apache-pulsar-java/commit/640e6c92a29eaa1111e18b5c927e603156fd37da))
* improve instructions for setting pulsar manager UI ([#9](https://github.com/numaproj-contrib/apache-pulsar-java/issues/9)) ([7c49b84](https://github.com/numaproj-contrib/apache-pulsar-java/commit/7c49b84e5dc8685e06f5e3b5d95cf20847a787e2))
* improve local setup documentation ([#49](https://github.com/numaproj-contrib/apache-pulsar-java/issues/49)) ([ba19ac8](https://github.com/numaproj-contrib/apache-pulsar-java/commit/ba19ac83dcba33c6c85b19dbed0ac658bf68fe5f))
* schema validation testing scripts ([#61](https://github.com/numaproj-contrib/apache-pulsar-java/issues/61)) ([b279b08](https://github.com/numaproj-contrib/apache-pulsar-java/commit/b279b08c74b0195d380dcf9f6937bb4ced67a8af))
* updated docs to work with jwt token auth ([#33](https://github.com/numaproj-contrib/apache-pulsar-java/issues/33)) ([c34e977](https://github.com/numaproj-contrib/apache-pulsar-java/commit/c34e9771c66aa6c7f37e72f62738871655ba0cd5))
* user guide ([#36](https://github.com/numaproj-contrib/apache-pulsar-java/issues/36)) ([4cbaa28](https://github.com/numaproj-contrib/apache-pulsar-java/commit/4cbaa285e0534cdb085812cb5a4842b15c12fe88))
