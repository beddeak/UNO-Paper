# UNO

마인크래프트 안에서 UNO를 플레이하는 카드 미니게임입니다.

[다운로드](https://github.com/beddeak/UNO-Paper/releases/latest)

## 설치 및 설정

Paper 26.3 · Java 25. 새 서버는 서버팩 ZIP을 빈 폴더에 풀고 Windows에서 `start.bat`을 실행하세요. 첫 실행 후 생성된 `eula.txt`를 읽고 동의하면 값을 변경한 뒤 다시 실행하세요. 기존 서버는 정지 후 `UNO-1.0.0.jar`을 `plugins/`에 넣고 다시 시작하세요.

첫 실행 후 `plugins/UNO/config.yml`을 편집하세요. 서버를 정상 종료하고 편집한 뒤 다시 시작하면 새 설정을 읽습니다. [기본 설정과 항목별 설명](src/main/resources/config.yml)

혼자라면 `/uno test`로 시작하세요. 외부 접속자의 카드 그림이 보이려면 리소스팩 설정의 `public-host` 또는 `external-url`을 서버 환경에 맞게 지정하세요.

## 명령어

| 명령어 | 설명 |
| --- | --- |
| `/uno test [봇 수]` | 혼자 봇과 시작. 기본 봇 3명 |
| `/uno start [이름...] [봇 수]` | 사람·봇 합계 2~10명으로 시작. 이름 생략 시 대기열을 사용하고, 이름·대기열·봇이 모두 없으면 관전자 모드가 아닌 접속자 전원 |
| `/uno stop` | 강제 종료. 인벤토리를 돌려줍니다 |
| `/uno join`, `/uno leave` | 다음 판 대기열 참가/취소 |
| `/uno table` | 바라보는 블록 위를 테이블 중심으로 정하기 |
| `/uno arcade` | 경기장으로 다시 이동. 서버 접속 시에는 자동 입장 |
| `/uno arcade exit` | 경기장 입장 전 위치로 복귀(게임 참가 중에는 사용 불가) |
| `/uno rule [규칙] [값]` | 규칙 보기/바꾸기(다음 판부터) |
| `/uno kick <이름>` | 게임에서 빼기(카드는 덱 밑으로) |
| `/uno pack [all\|이름]` | 리소스팩 다시 받기/보내기 |
| `/uno score [reset]` | 누적 점수 |
| `/uno status` | 현재 상태 |
| `/uno reload` | `config.yml` 다시 읽기 |
| 카드 | 설정 키 | 기본 장수 | 기본 확률(133장 중) | 원래 UNO |
| --- | --- | --- | --- | --- |
| 6 (박수) | `six-cards` | 16 | 12.0% | 8장(7.4%) |
| +2 | `plus2-cards` | 16 | 12.0% | 8장(7.4%) |
| 와일드 +4 | `plus4-cards` | 8 | 6.0% | 4장(3.7%) |
| 와일드 +10 | `plus10-cards` | 4 | 3.0% | 없음 |
| 와일드 +99 (무적) | `plus99-cards` | 1 | 0.75% | 없음 |

## 설정

`plugins/UNO/config.yml`에 설명이 있습니다. 자주 바꾸는 값:

| 키 | 기본값 | 내용 |
| --- | --- | --- |
| `rules.hand-size` | 7 | 처음 카드 수 |
| `rules.stacking` | true | +2/+4/+10 서로 누적(+99는 누적에 못 끼고 그 위에도 못 냄) |
| `rules.draw-until-playable` | true | 낼 수 있는 카드가 나올 때까지 뽑기(false = 1장) |
| `rules.call-penalty` | 2 | 안 외치다 걸리면 받는 장수 |
| `rules.turn-seconds` | 30 | 턴 제한 시간(0 = 무제한) |
| `rules.six-clap` | true | 6이 나오면 박수, 제일 늦은 사람 1장 |
| `rules.clap-seconds` | 5 | 박수 제한 시간(초, 1~30) |
| `rules.six-cards` | 16 | 덱 한 묶음의 6 장수(0~40, 원래 8) |
| `rules.plus2-cards` | 16 | 덱 한 묶음의 +2 장수(0~40, 원래 8) |
| `rules.plus4-cards` | 8 | 덱 한 묶음의 와일드 +4 장수(0~40, 원래 4) |
| `rules.plus10-cards` | 4 | 덱 한 묶음의 와일드 +10 장수(0~40, 원래 없음) |
| `rules.plus99-cards` | 1 | 덱 한 묶음의 와일드 +99(무적) 장수(0~40, 1장 = 0.75%) |
| `table.felt-size` | 3.0 | 펠트 크기(0 = 안 깖) |
| `table.card-scale` | 1.3 | 테이블 위 카드 크기 |
| `table.seat-radius` | 3.0 | 둘러 서는 반지름 |
| `table.teleport-players` | true | 시작할 때 자리로 이동 |
| `game.protect-players` | true | 게임 중 피해·배고픔 없음 |
| `game.draw-interval-ticks` | 16 | 받는 카드 한 장마다 걸리는 시간(틱, 날아오는 8틱 포함, 1~60) |


