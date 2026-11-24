# 부정행위자 답안 패턴 분석 — 연구 정리

작성일 2026-11-23. README의 "③ 감독관 보조" 중 **답안·시간 검사**를 넓히기 위한 조사입니다.
문헌 조사 → 이 시험(6문항)에서 통하는지 시뮬레이션 → 현재 구현과 비교 → 권고 순서로 정리했습니다.

## 1. 공격 방식별로 답안에 남는 흔적

| 공격 방식 | 답안에 남는 흔적 | 연구 분야 | 현재 구현 |
| --- | --- | --- | --- |
| 옆 사람 답 베끼기 | 두 사람의 답이 우연보다 많이 같음, 특히 **같은 오답** | 답 베끼기 지수 (ω, GBT, K-index) | `AnswerCheck` (숫자 문항, 2문항 이상) |
| 유출된 문제로 미리 공부 (사전 지식) | 유출 문항만 **정답률↑, 시간↓** | 사전 지식 탐지 (gated IRT, 응답 시간 모형) | 없음 |
| 어려운 문항만 AI·외부 도움 | 쉬운 건 틀리고 어려운 건 맞힘 = **실력과 안 맞는 패턴** | 개인 적합도 (lz) | 없음 |
| 전 문항 실시간 AI 커닝 | 고르게 높은 점수, 계산 문제치고 **짧고 고른 풀이 시간** | 응답 시간 이상 탐지 | "너무 빠른 정답" (숫자 문항, 예상 시간 25% 미만) |

## 2. 문헌 조사

### 2.1 답 베끼기 지수 (짝 비교)

- **원리**: "베낀 사람(copier)–보여 준 사람(source)" 쌍마다 실제로 같은 답 수와, 두 사람의 실력만으로 기대되는 같은 답 수를 비교합니다.
- **ω (Wollack 1997)**: 명목 반응 모형으로 기대 일치를 계산한 z 통계량. g2보다 1종 오류와 검정력이 모두 나았고, 크기를 지키는 지수 중 가장 강력한 편입니다. 다만 **베낀 문항 비율이 작으면 검정력이 낮습니다**. 이를 고친 ω_m도 제안되었습니다.
- **GBT (van der Linden & Sotaridona 2006)**: 같은 답 수의 일반화 이항분포 꼬리 확률. Zopluoglu & Davenport (2012)가 ω와 검정력을 비교했습니다.
- **K-index (Holland 1996, ETS)**: **같은 오답** 수를 이항 모형으로 봅니다. 보수적이고, source의 오답이 적으면 검정력이 거의 없습니다 (Lewis & Thayer 1998). 변형으로 K1·K2, S1·S2 (Sotaridona & Meijer)가 있습니다.
- **Harpp–Hogan 계열**: 같은 오답만 세는 단순 지수라 오탐이 많고 확률 기준이 없습니다. 쓰지 않는 편이 좋습니다.
- **이 프로젝트와의 관계**: 문헌의 지수는 **모든 학생이 같은 시험지**라는 가정에서 "같은 답이 우연인가"를 따집니다. ExamGuard는 학생마다 숫자가 달라서 **"내 시험지로는 오답인데 남의 시험지 정답과 일치"**를 직접 볼 수 있고, 이 일치가 우연히 나올 확률은 매우 낮습니다. 그래서 `AnswerCheck`는 K-index보다 강한 신호입니다 (SelfTest: 3문항 베낌 37/40 탐지, 흔한 실수 0/40 오탐).

### 2.2 개인 적합도 (실력과 안 맞는 패턴)

- **lz (Drasgow 등 1985)**: 문항반응이론(IRT)에서 응답 패턴의 로그우도를 표준화한 값. 낮을수록 "이 실력이면 나오기 어려운 패턴"입니다. θ를 추정해서 쓰면 표준정규분포를 따르지 않아 보정판 **l\*z**가 있습니다.
- **문항 수 문제**: 대부분의 적합도 통계는 긴 시험이 있어야 이상 패턴을 믿을 만하게 잡습니다 (Conijn 등 2014). 무작위 응답은 잘 잡지만, "좋게 보이려고 고친" 응답은 거의 못 잡았다는 보고도 있습니다.
- **AI 응답에 적용**: Strugatski & Alexandron (LAK 2025)은 IRT 적합도로 사람 응답과 ChatGPT·Claude·Gemini 응답을 구분했습니다. Sorenson & Hanson은 Rasch 모형에서 ChatGPT가 **쉬운 문항은 틀리고 어려운 문항은 맞히는** 사람답지 않은 패턴을 보였다고 보고했습니다. 다만 Warwick 프로젝트 요약은 학습장애 같은 정당한 이유로도 적합도가 나빠질 수 있다고 경고합니다.

### 2.3 응답 시간

- **로그정규 응답 시간 모형 (van der Linden 2006)**: log(시간) = 문항 시간 강도 − 응시자 속도 + 오차(σ). 응답 시간 연구 대부분의 기반입니다.
- **이상 시간 탐지**: 잔차 기반 검사와 베이즈 사후 예측 검사가 있습니다 (van der Linden & van Krimpen-Stoop 2003; van der Linden & Guo 2008). 베이즈 쪽이 탐지율은 높지만 오탐도 많았습니다.
- **공모 탐지**: 이변량 로그정규 모형으로 두 응시자의 시간 상관을 검정합니다 (van der Linden 2009). 저자는 **다른 증거로 확증해야 한다**고 강조합니다.
- **사전 지식**: Sinharay (2020)는 응답 시간 기반 통계를 제안했습니다. 결정적 gated IRT (Shu 등 2013)와 그 응답 시간 확장 DG-LNRT (Kasli 등 2023)는 "유출된 문항 × 유출받은 사람"에서만 빨라지고 정확해지는 구조를 모형화합니다. 실제 데이터에서는 사전 지식이 시간에 미치는 영향이 상황마다 달랐습니다 (Zopluoglu 등 2020).

### 2.4 행동 로그

- Akçapınar (2025, 52명, 예비 연구): 문제 글 선택, 우클릭, 창 포커스 이탈이 많은 학생이 평균 30~40점 높았습니다. ExamGuard의 궤적 분석과 같은 방향입니다. 창 포커스 이탈은 아직 기록하지 않습니다.

## 3. 시뮬레이션: 이 시험 규모에서 통하는가

`docs/research/AnswerPatternSim.java` (외부 라이브러리 없음, 고정 시드라 결과가 재현됨).
Rasch 모형, 문항 난이도는 −2~+2에 고르게 배치, 성실 응시자 20,000명 / 부정 응시자 5,000명입니다.
문턱은 성실 응시자 lz 분포의 하위 5%·1%로 정해 오탐률을 고정하고 탐지율을 잽니다.

### 3.1 lz 탐지율

| 문항 수 | A: 어려운 문항만 AI 도움 (오탐 5% / 1%) | B: 전 문항 AI 도움 (오탐 5% / 1%) |
| --- | --- | --- |
| **6 (이 시험)** | 54.8% / 42.6% | 17.5% / 9.0% |
| 20 | 92.1% / 80.1% | 21.0% / 6.7% |
| 40 | 98.7% / 94.9% | 30.8% / 8.8% |
| 80 | 99.9% / 99.2% | 52.9% / 19.7% |

- **A형 (실력이 낮은데 어려운 것만 맞힘)**: 6문항으로도 절반쯤 잡히지만, 오탐 5%는 40명 반에서 **2명이 잘못 지목**된다는 뜻입니다. 20문항 이상이 되어야 쓸 만합니다.
- **B형 (전 문항 AI)**: 문항 수를 늘려도 잘 잡히지 않습니다. 고르게 맞힌 답안은 "실력 좋은 사람"과 패턴이 같기 때문입니다. **답안 패턴만으로는 실시간 AI 커닝을 막지 못한다**는 README의 결론을 수치로 뒷받침합니다.
- **결과가 실제보다 좋게 나온 이유**: 문항 난이도를 참값으로 안다고 가정했습니다. 실제로는 수백 명 규모의 응답으로 추정해야 하고 (40명으로는 불가능), 추정 오차가 더해집니다. 또 6문항은 lz 값이 몇 가지로만 나와서 "정확히 5%" 문턱을 잡기 어렵습니다.

### 3.2 "너무 빠른 정답" 규칙의 오탐률

현재 규칙은 숫자 문항을 예상 시간의 25%보다 빨리 맞히면 확인 권장입니다. 예상 시간을 성실 응시자의 중앙값으로 보고, 시간 퍼짐 σ(로그 척도 표준편차)를 바꿔 보았습니다.
정답 여부는 무시했으므로 실제 오탐률은 이보다 조금 낮습니다 (상한).

| σ | 문항당 오탐 | 숫자 3문항 중 하나라도 |
| --- | --- | --- |
| 0.3 | 0.00% | 0.00% |
| 0.5 | 0.26% | 0.79% |
| 0.8 | 4.22% | 12.14% |
| 1.0 | 8.25% | 22.77% |

- **같은 25% 규칙이라도 오탐률은 σ에 따라 0%에서 23%까지 바뀝니다.** 이 시험의 σ는 아직 모릅니다.
- 그래서 고정 비율보다 **실측 분포의 분위수**(예: 성실 응시자 하위 0.5%)로 문턱을 정해야 합니다.

## 4. 현재 구현 평가

| 신호 | 판단 | 근거 |
| --- | --- | --- |
| `AnswerCheck` (남의 정답과 2문항 이상 일치) | **유지. 문헌 지수보다 강함** | 개인화 숫자 덕분에 우연 일치 확률이 매우 낮음. 문헌의 ω·K-index는 같은 시험지 전제라 이 상황에는 필요 없음 |
| 너무 빠른 정답 (25%) | **유지하되 문턱 재설정 필요** | 오탐률이 σ에 크게 좌우됨 (3.2). AI를 쓰고 일부러 기다리면 쉽게 피함 |
| 객관식 제외 | **보완 여지 있음** | 아래 5-2 |

`data/`의 기록 4건은 개발 중 시험 응시(0점·방치 포함)라서 문턱을 맞추는 데 쓸 수 없습니다.

## 5. 권고 (우선순위 순)

1. **실측 데이터부터 모으기**: README가 궤적에 대해 이미 요구한 "실제 사람 20명 이상"과 같은 세션에서, 문항별 머문 시간도 모아 문항별 σ를 추정합니다. 그다음 "너무 빠른 정답" 문턱을 `exp(μ + z·σ)` (z = 하위 0.5% 분위)로 바꿉니다. 코드는 `ReviewAdvisor.TOO_FAST_RATIO` 한 곳만 고치면 됩니다.
2. **객관식 보기 섞임을 베끼기 검사에 활용 (아이디어)**: 보기 순서가 학생마다 다르므로, 옆 사람의 **번호**를 베끼면 내 시험지에서는 다른 내용이 됩니다. "내 오답 번호 = 그 학생의 정답 번호"는 숫자 문항과 같은 방식으로 볼 수 있습니다. 다만 우연 일치 확률이 문항당 약 1/3이라 객관식 3문항만으로는 약하고, 숫자 문항과 합쳐 셀 때만 의미가 있습니다. 구현 전에 SelfTest 방식으로 오탐률을 먼저 잴 것.
3. **적합도(lz)는 지금 구현하지 않음**: 6문항·난이도 미추정 상태에서는 오탐을 감당할 수 없습니다 (3.1). 문제 은행이 20문항 이상으로 늘고 응시 기록이 수백 건 쌓인 뒤에 l\*z로 검토합니다.
4. **창 포커스 이탈 기록 (선택)**: 행동 로그 연구 (2.4)와 맞고, `TrajectoryRecorder`에 이벤트 하나만 더하면 됩니다. 부정행위가 아니어도 생기므로 단독 신호로 쓰지 않습니다.
5. **실시간 AI 커닝**: 답안·시간 패턴으로는 일부만 걸러집니다 (B형 9~18%). README 위협 모델대로 하드웨어·감독 대책의 영역으로 둡니다.

**원칙 (기존 README와 같음)**: 모든 통계 신호는 "확인 권장" 우선순위일 뿐 처분 근거가 아닙니다. 응답 시간 연구의 저자도 통계 결과를 다른 증거로 확증하라고 강조합니다 (van der Linden 2009).

## 6. 한계

- 시뮬레이션은 Rasch 모형·참값 문항 모수·가상의 부정 패턴(도움받은 문항 90% 정답)에 기반합니다. 경향 비교용이며 실제 탐지율로 인용하면 안 됩니다.
- 문헌 수치는 초록·요약에서 확인한 것이고, 원문 전체(특히 Zopluoglu & Davenport 2012의 검정력 표)는 읽지 않았습니다. 보고서에 인용하기 전에 원문을 확인할 것.

## 참고문헌

- Wollack (1997) ω, Zopluoglu & Davenport (2012) GBT·ω 비교 — [Miami 학술 저장소](https://scholarship.miami.edu/esploro/outputs/journalArticle/The-Empirical-Power-and-Type-I/991031560235302976), [van der Linden & Sotaridona 2006 (JEBS)](https://ideas.repec.org/a/sae/jedbes/v31y2006i3p283-304.html), [최적 베끼기 지수 유도 (arXiv 1409.7675)](https://arxiv.org/pdf/1409.7675), [ω 개선 알고리즘](https://www.bohrium.com/paper-details/an-algorithm-to-improve-test-answer-copying-detection-using-the-omega-statistic/811150921735077888-15076), [CopyDetect R 패키지](https://www.rdocumentation.org/packages/CopyDetect/versions/1.2/topics/CopyDetect1)
- Holland (1996) K-index — [ETS RR-96-07](https://www.ets.org/research/policy_research_reports/publications/report/1996/hxts.html); Lewis & Thayer (1998) — [ETS RR-98-49](https://www.ets.org/research/policy_research_reports/publications/report/1998/hxyl.html); [K 변형 정리](https://assess.com/?p=30318); [Harpp–Hogan 지수](https://assess.com/harpp-and-hogan-1993-response-similarity-index/); [탐지 지수 개관 (Thiessen)](https://bradthiessen.com/html5/docs/statdetect.pdf)
- 개인 적합도 — [Conijn 등 (Tilburg)](https://research.tilburguniversity.edu/en/publications/statistic-lz-based-person-fit-methods-for-non-cognitive-multiscal/), [문항 수별 적합도 비교 (JP3I 2024)](https://journal.uinjkt.ac.id/index.php/jp3i/article/view/34601), [PARE 논문](https://openpublishing.library.umass.edu/pare/article/1419/galley/1370/view)
- AI 응답 — [Strugatski & Alexandron, IRT로 사람·생성형 AI 응답 구분 (arXiv 2412.02713)](https://arxiv.org/html/2412.02713v2), [Warwick 프로젝트 요약](https://warwick.ac.uk/fac/cross_fac/eduport/edufund/projects/yang/projects/applying-irt-to-distinguish-between-human-and-generative-ai-responses-to-multiple-choice-assessments/), [Sorenson & Hanson 보도 (phys.org)](https://phys.org/news/2024-08-statistical-analysis-chatgpt-multiple-choice.pdf), [Akçapınar 행동 분석 (arXiv 2510.18881)](https://arxiv.org/pdf/2510.18881)
- 응답 시간 — [van der Linden & Guo 2008](https://www.cambridge.org/core/journals/psychometrika/article/abs/bayesian-procedures-for-identifying-aberrant-responsetime-patterns-in-adaptive-testing/85CFBD47811965DD943573EB7905CD37), [van der Linden & van Krimpen-Stoop 2003](https://www.cambridge.org/core/journals/psychometrika/article/abs/using-response-times-to-detect-aberrant-responses-in-computerized-adaptive-testing/D507DC52513DEF21367AA9A296E3DC3A), [이변량 공모 탐지 2009](https://research.utwente.nl/en/publications/a-bivariate-lognormal-response-time-model-for-the-detection-of-co/), [Sinharay 2020](https://pmc.ncbi.nlm.nih.gov/articles/PMC7433384), [DG-LNRT (Kasli 등)](https://scholarship.miami.edu/esploro/outputs/doctoral/A-Deterministic-Gated-Lognormal-Response-Time/991031752419702976), [gated IRT 적용 (해부학)](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6659220/), [Zopluoglu 등 2020](https://ifp.nyu.edu/?p=1571786), [DG-RLn (Iowa)](https://iro.uiowa.edu/esploro/outputs/doctoral/Advancing-test-security-a-new-detection/9984948237802771)
