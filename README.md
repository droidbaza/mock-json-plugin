# mock-json-plugin
gradle plugin for easy loading  and saving jsons responses from server for testing.Intead of manual loading and saving json responces into test resaources - all you need that is apply this plugin, 
run command `./gradlew loadMocks` and all needed requests authomatically will be loaded and saved into test/resources directory.

## How to add
Add to your **root project's** `build.gradle`:

For **Groovy**
```groovy
buildscript {
    repositories {
        maven { 
            url "https://plugins.gradle.org/m2/" 
        }
    }
    dependencies {
        classpath "org.droidbaza:mock-json-plugin:1.0.0-alpha05"
    }
}
```

For **Kotlin DSL**
```kotlin
buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("org.droidbaza:mock-json-plugin:1.0.0-alpha05")
    }
}
```

## Simple configuration
Add to your **app module's** `build.gradle`:

```kotlin
plugins {
    id 'org.droidbaza.mock-json'
}
```
and set up plugin configuration with base url address and list
 of the basic server requests that needs to be loaded.
```kotlin
mockJsonConfig{
    baseUrl ="https://example.com/"
    requests = [
            "EXAMPLE_REQUEST_NAME":"api/example_request_path",
            ...
            ]
}
```
Next sync project and run command  `./gradlew loadMocks`

This setup will load json requests specified in configs and save all json responses into /test/resources package
and after successful loading automatically will generate file `MockConfig.kt` witch will contains information about requests.


### Example realization 

This example contains realization with usage mockwebserver,hilt,truth,retrofit,okhttp and coroutines

1.create base abcstract repository class 
```kotlin
@RunWith(JUnit4::class)
abstract class BaseRepository<T> {
    lateinit var mockWebServer: MockWebServer

    @Throws(IOException::class)
    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    abstract fun setUp()

    @Throws(IOException::class)
    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Throws(IOException::class)
    fun enqueueResponse(fileName: String, responseCode: Int = 200) {
        enqueueResponse(fileName, emptyMap(), responseCode)
    }

    @Throws(IOException::class)
    private fun enqueueResponse(
        fileName: String,
        headers: Map<String, String> = emptyMap(),
        responseCode: Int,
    ) {
        val inputStream = javaClass.classLoader!!.getResourceAsStream(fileName)
        val source = inputStream.source().buffer()
        val mockResponse = MockResponse()
        for ((key, value) in headers) {
            mockResponse.addHeader(key, value)
        }

        mockWebServer.enqueue(mockResponse.setResponseCode(responseCode)
            .setBody(source.readString(StandardCharsets.UTF_8)))
    }

    @Throws(IOException::class)
    fun enqueuePostResponse(jsonBody: String?=null, responseCode: Int = 200) {
        val mockedResponse = MockResponse()
        mockedResponse.setResponseCode(200)
        jsonBody?.let {
            mockedResponse.setBody(it)
        }
       mockWebServer.enqueue(mockedResponse)
    }

    fun createService(clazz: Class<T>): T {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .build()
            .create(clazz)
    }
}
```

2.implement abstract class for testing
```kotlin
@RunWith(JUnit4::class)
@ExperimentalCoroutinesApi
class ExampleRepositoryImplTest: BaseRepository<ApiEvents>() {

    @get: Rule
    val coroutinesRule = MainCoroutinesRule()

    @get: Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var service: ApiExample

    @Before
    override fun setUp() {
        service = createService(ApiExample::class.java)
    }

    @Test
    fun exampleRequestShouldReturnTrue() = runBlocking {
        enqueueResponse(MocksConfig.EXAMPLE_REQUEST_NAME)
       //this generated file will contains information about request and will reffered for json loaded file
       //body of this request will be refer to loaded json file

        val response = service.exampleRequestsAsync()
        val result = response.body()?.data?.map { it.toModel() }
        val request = mockWebServer.takeRequest()

        val methodValid = request.method=="GET"
        val pathValid = request.path?.isValidCharsOfCall()

        Truth.assertWithMessage("result not valid").that(result).isNotNull()
        Truth.assertWithMessage("method not valid").that(methodValid).isTrue()
        Truth.assertWithMessage("path not valid").that(pathValid).isTrue()
        Truth.assertWithMessage("size not valid").that(result?.size).isGreaterThan(0)
    }
}
```
