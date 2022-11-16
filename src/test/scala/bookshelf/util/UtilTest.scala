package bookshelf.util

import cats.effect.IO
import munit.CatsEffectSuite

class UtilSpec extends CatsEffectSuite {

  test("write and read from in-memory DB String -> String") {
    assertIO(
      for {
        tested <- bookshelf.util.EffectMap.make[IO, String, String]()
        _ <- tested.add("k1", "v1")
        _ <- tested.add("k2", "v2")
        _ <- tested.add("k3", "v3")
        retrieved <- tested.get("k1")
      } yield retrieved,
      Some("v1")
    )
  }
}
