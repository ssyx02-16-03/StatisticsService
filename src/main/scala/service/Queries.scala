package service

object Queries {
  val matchAll = "{\n  \"size\":10000,\n  \"query\":{\n    \"match_all\" :{    }    \n  }\n}"
  val test = "{\n  \"fields\": [\"Priority\"],\n  \"size\":1,\n  \"query\":{\n    \"match\" :{ \"Priority\": \"Gul\"   }\n  },\n   \"filter\" : {\n      \"range\" : {\n        \"CareContactRegistrationTime\" : {\n          \"gte\" : \"2016-02-29T07:16:00Z\",\n          \"lte\" : \"2016-09-29T07:16:00Z\"\n        }\n      }\n   }\n}"

  val nakme_count = "{\n  \"fields\": [],\n  \"query\" :{\n    \"match\": {\n      \"Team\": \"NAKME\"\n    }\n  }\n}"
  val nakki_count = "{\n  \"fields\": [],\n  \"query\" :{\n    \"match\": {\n      \"Team\": \"NAKKI\"\n    }\n  }\n}"
  val nakor_count = "{\n  \"fields\": [],\n  \"query\" :{\n    \"match\": {\n      \"Team\": \"NAKOR\"\n    }\n  }\n}"
  val nakba_count = "{\n  \"fields\": [],\n  \"query\" :{\n    \"match\": {\n      \"Team\": \"NAKBA\"\n    }\n  }\n}"
  val nakkk_count = "{\n  \"fields\": [],\n  \"query\" :{\n    \"match\": {\n      \"Team\": \"NAKKK\"\n    }\n  }\n}"
}


