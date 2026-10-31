"""Compile and exercise the production Shorts policy without an Android emulator.
Uses Kotlin compiler jars from a downloaded Gradle distribution. Models and the
JSON metadata helper are extracted unchanged from production source; annotations
and unrelated Android/network dependencies are omitted from the harness.
"""
import pathlib, subprocess, tempfile, urllib.request
root=pathlib.Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/com/gr4nt/itube'
gradle=next((pathlib.Path.home()/'.gradle/wrapper/dists').glob('gradle-*/**/lib/kotlin-compiler-embeddable-*.jar'),None)
if not gradle: raise SystemExit('Run ./gradlew --version first to download its Kotlin compiler.')
lib=gradle.parent; cp=':'.join(str(p) for p in lib.glob('*.jar'))
def method(text,name):
 start=text.index('    private fun '+name);brace=text.index('{',start);depth=1;i=brace+1
 while depth:
  if text[i]=='{':depth+=1
  if text[i]=='}':depth-=1
  i+=1
 return text[start:i].replace('private fun','fun',1)
with tempfile.TemporaryDirectory() as d:
 d=pathlib.Path(d)
 models=(src/'data/Models.kt').read_text();models=models[models.index('@Serializable'):models.index('/** Picks')].replace('@Serializable','')
 page=(src/'data/Repo.kt').read_text();page=page[page.index('class PageResult'):page.index('data class ChannelDetails')]
 (d/'Models.kt').write_text('package com.gr4nt.itube.data\n'+models+page)
 jsonjar=d/'json.jar';urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',jsonjar)
 parser=(src/'net/Innertube.kt').read_text();helpers='\n'.join(method(parser,n) for n in ['walk','hasShortsMetadata'])
 (d/'Metadata.kt').write_text('package com.gr4nt.itube.data\nimport org.json.JSONObject\nimport org.json.JSONArray\n'+helpers)
 (d/'Checks.kt').write_text('''package com.gr4nt.itube.data
import org.json.JSONObject
import kotlin.coroutines.*
fun main() {
 var checks=0
 fun verify(value:Boolean) { check(value) { "Check ${checks+1} failed" }; checks++ }
 fun video(url:String="https://www.youtube.com/watch?v=regular", seconds:Long=45, short:Boolean=false)=VideoItem(url,"Video",null,durationSec=seconds,isShort=short)
 verify(video(short=true).isShortContent)
 verify(video("https://youtube.com/shorts/abc?feature=share").isShortContent)
 verify(video("https://m.youtube.com/shorts/abc").isShortContent)
 verify(video("https://www.youtube.com/shorts/abc").isShortContent)
 verify(!video(seconds=15).isShortContent)
 verify(!video(seconds=60).isShortContent)
 verify(!video(seconds=180).isShortContent)
 verify(!video(seconds=181).isShortContent)
 verify(!isShortUrl("https://youtube.com.evil.test/shorts/abc"))
 verify(!isShortUrl("https://notyoutube.com/shorts/abc"))
 verify(!isShortUrl("broken url"))
 verify(!isShortUrl("https://youtube.com/watch?v=abc&next=/shorts/other"))
 val normal=video();val short=video(short=true);val channel=ChannelItem("channel","Creator",null);val playlist=PlaylistItem("list","Playlist",null)
 verify(listOf(normal,short).regularVideos()==listOf(normal))
 verify(listOf<Item>(short,channel,normal,playlist).regularItems()==listOf(channel,normal,playlist))
 verify(emptyList<Item>().regularItems().isEmpty())
 verify(hasShortsMetadata(JSONObject("""{"navigationEndpoint":{"reelWatchEndpoint":{"videoId":"abc"}}}""")))
 verify(hasShortsMetadata(JSONObject("""{"command":{"shortsVideoEndpoint":{"videoId":"abc"}}}""")))
 verify(hasShortsMetadata(JSONObject("""{"overlay":{"style":"SHORTS"}}""")))
 verify(hasShortsMetadata(JSONObject("""{"endpoint":{"url":"/shorts/abc"}}""")))
 verify(!hasShortsMetadata(JSONObject("""{"lengthText":{"simpleText":"0:45"},"title":"#shorts compilation","watchEndpoint":{"videoId":"abc"}}""")))
 var calls=0
 val page=PageResult<Item>(listOf(short), suspend { calls++; PageResult(listOf(short,normal,channel), suspend { calls++; PageResult(listOf(playlist),null) }) }).regularPage()
 verify(page.items.isEmpty());verify(calls==0);verify(page.next!=null)
 var failure:Throwable?=null
 suspend {
  val second=page.next!!();verify(second.items==listOf(normal,channel));verify(calls==1)
  val third=second.next!!();verify(third.items==listOf(playlist));verify(third.next==null);verify(calls==2)
 }.startCoroutine(object:Continuation<Unit> { override val context=EmptyCoroutineContext;override fun resumeWith(result:Result<Unit>){failure=result.exceptionOrNull()} })
 failure?.let { throw it }
 verify(listOf(short).size==1) // Filtering leaves stored lists intact.
 println("PASS: $checks Shorts policy, metadata and continuation checks.")
}
''')
 files=[str(d/'Models.kt'),str(d/'Metadata.kt'),str(d/'Checks.kt'),str(src/'data/ShortsPolicy.kt')]
 std=next(lib.glob('kotlin-stdlib-*.jar'))
 subprocess.run(['java','-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(std)+':'+str(jsonjar),'-d',str(d/'checks.jar'),*files],check=True)
 subprocess.run(['java','-cp',str(d/'checks.jar')+':'+str(std)+':'+str(jsonjar),'com.gr4nt.itube.data.ChecksKt'],check=True)
