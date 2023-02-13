
# push-pull-notifications-api

**Push Pull notifications must not be used with any OFFICIAL, OFFICIAL SENSITIVE, SECRET or TOP SECRET information.**

This API allows notifications to be sent (pushed) to software developers or allows the software developer to get (pull) 
notifications. Notifications are created by other HMRC services.

An example use case is for asynchronous API requests.
1. API X defines an *api-subscription-field* of type PPNS (see https://confluence.tools.tax.service.gov.uk/pages/viewpage.action?pageId=182540862)
1. Software developer subscribes to API X in Developer Hub and can optionally add an endpoint where notifications 
    will be pushed to. This automatically creates a PPNS box called [API_CONTEXT]##[API_VERSION]##[API_SUBSCRIPTION_FIELD_NAME]
    , Eg `hello/world##1.0##callbackUrl` 
1. Software developer makes an API request to API X
1. API X gets the client ID from either the `X-Client-Id` header or from an _auth_ retrieval.
1. API X makes a request to `GET /box` using the inferred box name and client ID to retrieve the box ID.
1. API X generates a correlation/request ID.
1. API X sends a response 200 HTTP status to the software developer with a body containing the box ID and 
    the correlation/request ID.
1. API X starts their asynchronous process, saving the state of this with the correlation/request ID and PPNS box ID.
1. API X complete their asynchronous process, retrieving correlation/request ID and PPNS box ID from state.
1. API X creates a _message_ which must contain the correlation/request ID, and POSTs it to PPNS using the box ID.
1. If the software developer has set the api-subscription-field, the notification is POSTed to their endpoint
    * The API consumer receives the notification and matches the correlation/request ID in the notification with the 
      correlation/request ID they got from the initial API request, they extract the message and process it accordingly.
1. If the API consumer chooses to call the PPNS get-notifications endpoint using the box ID, they can retrieve a 
    list of notifications.
    * The API consumer iterates over each notification and matches the correlation/request ID in the notification 
      with the correlation/request ID they got from the initial API request, they extract the message and process it accordingly.
# Manage box and callback Urls
## 3rd Party setup callback URL for a default box

[![](https://mermaid.ink/img/pako:eNp1k0tv2zAMx78KoV06LGneQOFDgW4dkAF7GE27ky-0TNtCZcmTpWJBkO8-yk7SDGl9sGjyx4ekv3dC2oJEIjr6E8hIuldYOWwyA_x45TUl8FgrV0CLzm9BWlOqKjhlKvA1QUElBu0ht39BotY5ymd4evgO1sA9vZC2LTlYh3yoiMFbE5qc3OFbeutg4QpmAbtoQdo3OiUPYOyupGrR-B46Zy8JTuaWEXpjhnMwTX9uInaXfoNUoy-ta3rnJXoXfN2jvL4R5QIcXP94-BLtS-AzHwyZfvCjOYG1LQZyeDuSHlyVX81Xq9F8Oh3x-hE-QBpcq-mcHE5sfHv7adhqAh350wU8OT1gQzBicU_vQDEUEa6ZwCYOdobAC2pVoFd8n7JmP5mKTjOMOW_cn2ECvyP4yvBmutaajs6aRPo476_n_0aMoYMMXmM8S2bESDTkGlQFy3QXA5lg5TWUiYTNgwAzkZk9o6HlYelroVhXIilRdzQSUXWbrZEi8S7QETpI_URpiwVx0k74bRv_iUp1nksOko_-4DS7a-_bLplMYvi6Ur4O-bW0zQQLbFqnisY6muByvsTZaoZTmiMtVzfTGZbyJl_I5aJcrEqx3-__ATSmJAY)](https://mermaid-js.github.io/mermaid-live-editor/edit/#pako:eNp1k0tv2zAMx78KoV06LGneQOFDgW4dkAF7GE27ky-0TNtCZcmTpWJBkO8-yk7SDGl9sGjyx4ekv3dC2oJEIjr6E8hIuldYOWwyA_x45TUl8FgrV0CLzm9BWlOqKjhlKvA1QUElBu0ht39BotY5ymd4evgO1sA9vZC2LTlYh3yoiMFbE5qc3OFbeutg4QpmAbtoQdo3OiUPYOyupGrR-B46Zy8JTuaWEXpjhnMwTX9uInaXfoNUoy-ta3rnJXoXfN2jvL4R5QIcXP94-BLtS-AzHwyZfvCjOYG1LQZyeDuSHlyVX81Xq9F8Oh3x-hE-QBpcq-mcHE5sfHv7adhqAh350wU8OT1gQzBicU_vQDEUEa6ZwCYOdobAC2pVoFd8n7JmP5mKTjOMOW_cn2ECvyP4yvBmutaajs6aRPo476_n_0aMoYMMXmM8S2bESDTkGlQFy3QXA5lg5TWUiYTNgwAzkZk9o6HlYelroVhXIilRdzQSUXWbrZEi8S7QETpI_URpiwVx0k74bRv_iUp1nksOko_-4DS7a-_bLplMYvi6Ur4O-bW0zQQLbFqnisY6muByvsTZaoZTmiMtVzfTGZbyJl_I5aJcrEqx3-__ATSmJAY)

## 3rd Party create and set callback URL for client managed box

[![](https://mermaid.ink/img/pako:eNp9k0tv2zAMgP8KoV02LGneQOFDgT4GpIdtxpL0lAst0bZQWfJkOWhQ5L-PspM0QIr5YNPkx7f0LqRTJBLR0N-WrKQnjYXHamuBn6CDoQTWpfYKavRhD9ITBmoAQRpNNkCFFgtSkLk32GmENP21gvv0uY-AbXC2rTLyx38ZnIeZV0-0A2yiBGkXmBVkXH0CYzYtdY2cIkKX7DXBzss2i9A5DLDiGuyqY4wLhNRgyJ2vOuU1et-GskP5-4mVA7Bx-fPP40e3l8ADyleyXeEncQRLp3qyf3uSAXyRfZ0uFoPpeDzg7zf4Amnra0OXZD-x4d3d91hsAulmncBjt4s4-R6KpiEzw55O4MG9PR8TWsek10UZwOVwAtYl8UrRQijJgquDdhaN2UNDIa6Y5YyL33jznzI2tYplXMFdOYyyUwKrOIALBHZoNPtxQpAl68kWdE7SddHtKoGXCH4wPLSmdrahT3tm_vdrb-GEWysGoiJfoVZ8xt-jYSu414q2ImFRUY6tCVuxtQdG266TH0rzIRVJjqahgYhHeLW3UiTBt3SCjvfkTBmHitjpXYR9HS9UoZvAIaWzuS6ivvWG1WUIdZOMRtF8U-hQttmNdNUIFVa116pynkY4n85xspjgmKZI88XteIK5vM1mcj7LZ4tcHA6Hf-DyNl4)](https://mermaid-js.github.io/mermaid-live-editor/edit/#pako:eNp9k0tv2zAMgP8KoV02LGneQOFDgT4GpIdtxpL0lAst0bZQWfJkOWhQ5L-PspM0QIr5YNPkx7f0LqRTJBLR0N-WrKQnjYXHamuBn6CDoQTWpfYKavRhD9ITBmoAQRpNNkCFFgtSkLk32GmENP21gvv0uY-AbXC2rTLyx38ZnIeZV0-0A2yiBGkXmBVkXH0CYzYtdY2cIkKX7DXBzss2i9A5DLDiGuyqY4wLhNRgyJ2vOuU1et-GskP5-4mVA7Bx-fPP40e3l8ADyleyXeEncQRLp3qyf3uSAXyRfZ0uFoPpeDzg7zf4Amnra0OXZD-x4d3d91hsAulmncBjt4s4-R6KpiEzw55O4MG9PR8TWsek10UZwOVwAtYl8UrRQijJgquDdhaN2UNDIa6Y5YyL33jznzI2tYplXMFdOYyyUwKrOIALBHZoNPtxQpAl68kWdE7SddHtKoGXCH4wPLSmdrahT3tm_vdrb-GEWysGoiJfoVZ8xt-jYSu414q2ImFRUY6tCVuxtQdG266TH0rzIRVJjqahgYhHeLW3UiTBt3SCjvfkTBmHitjpXYR9HS9UoZvAIaWzuS6ivvWG1WUIdZOMRtF8U-hQttmNdNUIFVa116pynkY4n85xspjgmKZI88XteIK5vM1mcj7LZ4tcHA6Hf-DyNl4)

### CallbackUL Validation

The details on the validation of the callbackURL with the 3rd party are documented here: [External Push Pull Notifications API](https://developer.service.hmrc.gov.uk/api-documentation/docs/api/service/push-pull-notifications-api/1.0)

## API sending a notification
### For a default box

[![](https://mermaid.ink/img/pako:eNp9VE1v2zAM_SuEdmiKJU2aD6DwoUDXDm0P3YJ22ykXWaJtobbkyVKWoMh_HyV_rG2K-RDT5OPTI8XwhQkjkSWswd8etcAbxXPLq40GepxyJSawXn97gp-N0jmsfVOAj6bEjPvSQWp2LZp7Z7SvUrTdt3DGwsLKG9wCb4IFa27dHsiBpal7YE1OJVTNtYug19hjBCXf-TSABhogxzEwyibY1foe1iV3mbFVdB5Dr7wrIpTeH0SJgIJ3D4_XwT4GfOHiGXUU3ptTuDOyRba_FoUDm6ej-Ww2hvlqNSbjFD7BrUXULUYbh2C2GLuWwAN_RuLcaxElXPOyfIUrMXNgshBK4EehGuB1XSpsQGXgCgRBHyROGnJRBigtSi8xxk52E0F0Kamd0AVOlDyBArnsryT0f3J52ZKHwwMaRn8U9Umb_6SfDnevtpxUDv0ytYPvtVNGU-IecnSdwHvqGzWMaO7l-zZ0tUUaqPgeCr6lAkxM_1cjUZi25tgCwcMxDbWcZrpxkX7LSyVJUHtCoIvlhXtP4Pa1mBEPTovOKqSso4KGEYnJXZOIZNQzdBkSP8ih0Xij4HMc0gR-dfIGGS0qRieEm7Rn9DD5DhfZTvvhezM5YpiaCAp643A9ovNWw2i-230guL81YmNjVqGtuJK0Jl6Ce8Oo1RVuWEJmtwY2bKMPBPV10PdVKvrvsyTjZYNjFjbDE4lhibMee1C3agZUacIAseSFuX0ddlKuGkeUwuhM5cHvbUnuwrm6SabTED7LaSJ9eiZMNeWSV7VVsjIWp3w5X_Lz1Tmf4ZzjcnUxO-eZuEgXYrnIFquMHQ6Hv8cEnVM)](https://mermaid-js.github.io/mermaid-live-editor/edit/#pako:eNp9VE1v2zAM_SuEdmiKJU2aD6DwoUDXDm0P3YJ22ykXWaJtobbkyVKWoMh_HyV_rG2K-RDT5OPTI8XwhQkjkSWswd8etcAbxXPLq40GepxyJSawXn97gp-N0jmsfVOAj6bEjPvSQWp2LZp7Z7SvUrTdt3DGwsLKG9wCb4IFa27dHsiBpal7YE1OJVTNtYug19hjBCXf-TSABhogxzEwyibY1foe1iV3mbFVdB5Dr7wrIpTeH0SJgIJ3D4_XwT4GfOHiGXUU3ptTuDOyRba_FoUDm6ej-Ww2hvlqNSbjFD7BrUXULUYbh2C2GLuWwAN_RuLcaxElXPOyfIUrMXNgshBK4EehGuB1XSpsQGXgCgRBHyROGnJRBigtSi8xxk52E0F0Kamd0AVOlDyBArnsryT0f3J52ZKHwwMaRn8U9Umb_6SfDnevtpxUDv0ytYPvtVNGU-IecnSdwHvqGzWMaO7l-zZ0tUUaqPgeCr6lAkxM_1cjUZi25tgCwcMxDbWcZrpxkX7LSyVJUHtCoIvlhXtP4Pa1mBEPTovOKqSso4KGEYnJXZOIZNQzdBkSP8ih0Xij4HMc0gR-dfIGGS0qRieEm7Rn9DD5DhfZTvvhezM5YpiaCAp643A9ovNWw2i-230guL81YmNjVqGtuJK0Jl6Ce8Oo1RVuWEJmtwY2bKMPBPV10PdVKvrvsyTjZYNjFjbDE4lhibMee1C3agZUacIAseSFuX0ddlKuGkeUwuhM5cHvbUnuwrm6SabTED7LaSJ9eiZMNeWSV7VVsjIWp3w5X_Lz1Tmf4ZzjcnUxO-eZuEgXYrnIFquMHQ6Hv8cEnVM)

### For a client managed box

[![](https://mermaid.ink/img/pako:eNp1VE1P4zAQ_SsjrxBF29LSDwnlgMTCCjiwW8Hunnpx7Eli4dhZx-m2Qv3vO3aSUlToIZ08P795npn4lQkrkSWsxr8NGoG3iueOlysD9PPKa0xgufzxDL9rZXJYNnUBTQwlZrzRHlK7adm88dY0ZYquexfeOpg5eYtr4HWIYMmd3wIBqG3VEysClVAVNz6SDrnHDNp836SBtJcBAo6J0TbRrpcPsNTcZ9aVETymXje-iFT6_2CVBGjx_vHpJsTHhG9cvKCJxvtwDPdWtsz26VB4cHk6mE4mQ5guFkMKzuAL3DlE03KM9Qh2jbFqCTzyFyTNrRHRwg3X-oCnMfNgs7CUwK9C1cCrSiusQWXgCwRBL2ROGaEbSXDATjcjQTIpuRxR40ZKnkKBXPatCHUfXV21oiFpYMPgn6L6fL73bN9wteZkbV-kY6M5-s7Yg4TM2fKg5iFfTB66kcDdIXXAA-jQO4Vrro8yvokE3qg7A6kMeolui8QPNp2cADXtnYuvcXxaF3RaSLfQHrplxdWQZtSm-fny2Yn_cK1kSPfWE1IBayIQpEvuRdE1qG9aV5wAvT_5gcmzftrejYrYj0kkhUrEaXpC3zgDg-lm80Ep-o6RGhuyEl3JlaR74TXAK0Y-SlyxhMLuu1-xldkRtanC4b5LRR87SzKuaxyycBU8kxmWeNdgT-rulj1L2zA8LHllfluFSyhXtSdJYU2m8oA3ThNceF_VyXgcls9zGsUmPRe2HHPJy8opWVqHYz6fzvnF4oJPcMpxvricXPBMXKYzMZ9ls0XGdrvdf2zElZs)](https://mermaid-js.github.io/mermaid-live-editor/edit/#pako:eNp1VE1P4zAQ_SsjrxBF29LSDwnlgMTCCjiwW8Hunnpx7Eli4dhZx-m2Qv3vO3aSUlToIZ08P795npn4lQkrkSWsxr8NGoG3iueOlysD9PPKa0xgufzxDL9rZXJYNnUBTQwlZrzRHlK7adm88dY0ZYquexfeOpg5eYtr4HWIYMmd3wIBqG3VEysClVAVNz6SDrnHDNp836SBtJcBAo6J0TbRrpcPsNTcZ9aVETymXje-iFT6_2CVBGjx_vHpJsTHhG9cvKCJxvtwDPdWtsz26VB4cHk6mE4mQ5guFkMKzuAL3DlE03KM9Qh2jbFqCTzyFyTNrRHRwg3X-oCnMfNgs7CUwK9C1cCrSiusQWXgCwRBL2ROGaEbSXDATjcjQTIpuRxR40ZKnkKBXPatCHUfXV21oiFpYMPgn6L6fL73bN9wteZkbV-kY6M5-s7Yg4TM2fKg5iFfTB66kcDdIXXAA-jQO4Vrro8yvokE3qg7A6kMeolui8QPNp2cADXtnYuvcXxaF3RaSLfQHrplxdWQZtSm-fny2Yn_cK1kSPfWE1IBayIQpEvuRdE1qG9aV5wAvT_5gcmzftrejYrYj0kkhUrEaXpC3zgDg-lm80Ep-o6RGhuyEl3JlaR74TXAK0Y-SlyxhMLuu1-xldkRtanC4b5LRR87SzKuaxyycBU8kxmWeNdgT-rulj1L2zA8LHllfluFSyhXtSdJYU2m8oA3ThNceF_VyXgcls9zGsUmPRe2HHPJy8opWVqHYz6fzvnF4oJPcMpxvricXPBMXKYzMZ9ls0XGdrvdf2zElZs)


## 3rd party receiving a PPNS notification
### Via a push to the callbackUrl

[![](https://mermaid.ink/img/pako:eNptkttu2zAMhl-F0G5SLGmOHgpfFOjWASl2qNGsd7mhJdoWakueLHULgrx7KSduV2QEbFPkJ_qXyL2QVpFIRUe_AxlJtxpLh83WAJvXvqYUsuznBh47bUrIQlcdcxi8NaHJyZ3W0lsHS6du6Rmwix5k6PwOOEC1bQew5aCWukXje-hf9pzgzeuQR-i1DHDgHOxFMnaT3UFWoy-sa_rgOXoTfNWj_P1Plgtwcv3j4Uv0z4HPKJ_I9MIHdwprq47k8e1IenBlPlokyRgWs9mYnwv4AK02T0ckWm1tC38qXRMY63WhJXptTQesHhBy-_eNjRbPM7m-_sgXxm253_wCbyMFEus6ZzHw-PAdRkMtzS0ChR4vBl2DcYEJF5rEgincf4NR1Pee4SoE9pn6rkIKdwWQcyzMUdeySBq_Ew2644x3mhRwq9wOEmj06SyfoLLBdW8_4Fs7LnpHjEVDrkGteBT3MbEVvqKGtiJlV1GBofZbsTUHRkPLZ6Kvio_nRFpgzVJEnMfNzkiRehdogE7j_ErVFhXxpr3wuzbOfak7zyWlNYUuYzy4msOV922XTqcxfVlqX4X8Utpmigqb1mnVWEdTXC1WOE_mOKMF0iq5ms2xkFf5Uq6WxTIpxOFweAFnqhQC)](https://mermaid-js.github.io/mermaid-live-editor/edit/#pako:eNptkttu2zAMhl-F0G5SLGmOHgpfFOjWASl2qNGsd7mhJdoWakueLHULgrx7KSduV2QEbFPkJ_qXyL2QVpFIRUe_AxlJtxpLh83WAJvXvqYUsuznBh47bUrIQlcdcxi8NaHJyZ3W0lsHS6du6Rmwix5k6PwOOEC1bQew5aCWukXje-hf9pzgzeuQR-i1DHDgHOxFMnaT3UFWoy-sa_rgOXoTfNWj_P1Plgtwcv3j4Uv0z4HPKJ_I9MIHdwprq47k8e1IenBlPlokyRgWs9mYnwv4AK02T0ckWm1tC38qXRMY63WhJXptTQesHhBy-_eNjRbPM7m-_sgXxm253_wCbyMFEus6ZzHw-PAdRkMtzS0ChR4vBl2DcYEJF5rEgincf4NR1Pee4SoE9pn6rkIKdwWQcyzMUdeySBq_Ew2644x3mhRwq9wOEmj06SyfoLLBdW8_4Fs7LnpHjEVDrkGteBT3MbEVvqKGtiJlV1GBofZbsTUHRkPLZ6Kvio_nRFpgzVJEnMfNzkiRehdogE7j_ErVFhXxpr3wuzbOfak7zyWlNYUuYzy4msOV922XTqcxfVlqX4X8Utpmigqb1mnVWEdTXC1WOE_mOKMF0iq5ms2xkFf5Uq6WxTIpxOFweAFnqhQC)

### Polling with the pull API

[![](https://mermaid.ink/img/pako:eNqNk29v2jAQxr_KyXsDGi3_pSovKrHRlUpbG61v88axL8HCsT3HYUOI775zAl1b2DQUyOXu58d3T_CeCSuRJazGHw0agUvFS8-rzAB9ggoaE0jTx2dIG62hIrgr8SZY01Q5-uOzCNbD1MslboHXMYKU-7ADSqC27gQ6SiqhHDehhV6z5wQtXjV5hF5kgBLnYNsjYYv0AVLNQ2F91SbP0UUT1i1K9wtVEqDi6tv3zzE-Bz5xsUHTNn4Kh7CysiO7X48igC_z3mQ-H8BkNBrQtw8fwCmz6RBjA4LdYmsaeWzJXmo65lWhBA_KmrpDtbWuA3gAZQL6LdddKVp3dXvbTprAPZIRd4_Lh8f7Szr0jtSW07Z_fGltiwJRKIGvqg5gi8si0Hv9eKUkfCRjdtpy2e_UJF7You3-C01GxTU48kuZ8o0y9FQB3Oz6F52Jnbn2n-S8FVjXdL1dHtZoLtmxEBtjf2qUJb7bL7e_qP8B6OO87war-_9lWAJPm38MTpNm5m9hZtiAVegrriQdv30sZowmqTBjCYUSC97okLHMHAhtnCT1O6nomLGk4LrGAYuH8HlnBEuCb_AEHY_wCxVfENKiPQs7F896SVOTpLCmUGXMN15Teh2Cq5PhMJavSxXWTX4tbDXkklfOK1lZj0M-m8z4eD7mI5xwnM1vRmNeiJt8KmbTYjov2OFw-A3PcWvz)](https://mermaid-js.github.io/mermaid-live-editor/edit/#pako:eNqNk29v2jAQxr_KyXsDGi3_pSovKrHRlUpbG61v88axL8HCsT3HYUOI775zAl1b2DQUyOXu58d3T_CeCSuRJazGHw0agUvFS8-rzAB9ggoaE0jTx2dIG62hIrgr8SZY01Q5-uOzCNbD1MslboHXMYKU-7ADSqC27gQ6SiqhHDehhV6z5wQtXjV5hF5kgBLnYNsjYYv0AVLNQ2F91SbP0UUT1i1K9wtVEqDi6tv3zzE-Bz5xsUHTNn4Kh7CysiO7X48igC_z3mQ-H8BkNBrQtw8fwCmz6RBjA4LdYmsaeWzJXmo65lWhBA_KmrpDtbWuA3gAZQL6LdddKVp3dXvbTprAPZIRd4_Lh8f7Szr0jtSW07Z_fGltiwJRKIGvqg5gi8si0Hv9eKUkfCRjdtpy2e_UJF7You3-C01GxTU48kuZ8o0y9FQB3Oz6F52Jnbn2n-S8FVjXdL1dHtZoLtmxEBtjf2qUJb7bL7e_qP8B6OO87war-_9lWAJPm38MTpNm5m9hZtiAVegrriQdv30sZowmqTBjCYUSC97okLHMHAhtnCT1O6nomLGk4LrGAYuH8HlnBEuCb_AEHY_wCxVfENKiPQs7F896SVOTpLCmUGXMN15Teh2Cq5PhMJavSxXWTX4tbDXkklfOK1lZj0M-m8z4eD7mI5xwnM1vRmNeiJt8KmbTYjov2OFw-A3PcWvz)

## Terminology

| Term | Description |
| --- | --- |
| _box_ | A container for notifications |
| _notification_ | A wrapper for a message. Notifications expire after 30 days |
| _message_ | XML or JSON that is being communicated from an API producer to an API consumer |
| _default box_ | the default box created automatically per application and API version subscription |
| _client mangaged box_ | a box create by a 3rf party and associated with a single applicaiton |

## `GET /box`
Return the details of a box

### Query parameters
| Name | Description |
| --- | --- |
| `boxName` (optional) | The name of the box to get. URL encoded. For example ```BOX%202``` |
| `clientId` (optional)| The Developer Hub Client ID that owns the box |

### Response
HTTP Status: `200`
```
{
    "boxId": "1c5b9365-18a6-55a5-99c9-83a091ac7f26",
    "boxName":"BOX 2",
    "boxCreator":{
        "clientId": "X5ZasuQLH0xqKooV_IEw6yjQNfEa"
    },
    "subscriber": {
        "subscribedDateTime": "2020-06-01T10:27:33.613+0000",
        "callBackUrl": "https://www.example.com/callback",
        "subscriptionType": "API_PUSH_SUBSCRIBER",
    }
}
```
| Name | Description |
| --- | --- |
| `boxId` | Identifier for a box
| `boxName` | Box name 
| `boxCreator.clientId` | Developer Hub Application Client ID, that created and has access to this box
| `subscriber` | Details of the subscriber to this box |
| `subscriber.subscribedDateTime` | ISO-8601 UTC date and time that the subscription was created |
| `subscriber.callBackUrl` | The URL of the endpoint where push notifications will be sent |
| `subscriber.subscriptionType` | The type of subscriber. Currently only `API_PUSH_SUBSCRIBER` is supported |

### Error scenarios
| Scenario | HTTP Status | Code |
| --- | --- | --- |
| Missing or incorrect query parameter | `400` | `BAD_REQUEST`
| Box does not exist | `404` | `BOX_NOT_FOUND`

## `PUT /box`
Create a new box

This endpoint is restricted, only a allowlist of services can access it.

### Request headers
| Name | Description |
| --- | --- |
| `Content-Type` | Either `application/json` or `text/json` 
| `User-Agent` | User agent that identifies the calling service 

### Request
```
{
    "boxName": "Box 1", 
    "clientId": "X5ZasuQLH0xqKooV_IEw6yjQNfEa"
}
```
| Name | Description |
| --- | --- |
| `boxName` | The name of the box to create |
| `clientId` | The Developer Hub Client ID that can access this box |

### Response
HTTP Status: `201` if the box is created
```
{
    "boxId": "1c5b9365-18a6-55a5-99c9-83a091ac7f26" 
}
```

HTTP Status: `200` if the box already exists
```
{
    "boxId": "1c5b9365-18a6-55a5-99c9-83a091ac7f26" 
}
```

### Error scenarios
| Scenario | HTTP Status | Code |
| --- | --- | --- |
| `boxName` or `clientId` missing from request body | `400` | `INVALID_REQUEST_PAYLOAD`
| Access denied, service is not allowlisted | `403` | `FORBIDDEN`

## `PUT /box/:boxId/callback`

Set the Callback URL for a box. This defines the endpoint on the third party's system that push notifications will be sent to.

Attempting to update the Callback URL triggers PPNS to perform a validation of the supplied endpoint. The third party's system must respond correctly before the Callback URL is updated.

### Path parameters
| Name | Description |
| --- | --- |
| `boxId` | Unique identifier for a box. Eg `1c5b9365-18a6-55a5-99c9-83a091ac7f26` |

### Request headers
| Name | Description |
| --- | --- |
| `Content-type` | Must be `application/json` 
| `User-Agent` | User agent that identifies the calling service

### Request
```
{
    "clientId": "838e5276-b9d7-4258-a018-ea5cb54f39a1",
    "callbackUrl": "https://www.example.com/callback"
}
```
| Name | Description |
| --- | --- |
| `clientId` | The Developer Hub Client ID that owns the box |
| `callbackUrl` | The URL of the endpoint where push notifications will be sent |

### Response
HTTP Status: `200`
```
{
    "successful": "true"
}
```
| Name | Description |
| --- | --- |
| `successful` | Whether the Callback URL was updated successfully or not

### Other `200` responses

In addition to the successful response above, it is also currently possible to receive a `200` response in other 
scenarios where the Callback URL has not been updated. In these cases the response will look like the following:

```
    "successful": "false",
    "errorMessage": "Reason for failure"
```

The scenarios where this can occur are as follows:

* PPNS has been unable to validate the Callback URL on the client's system
* PPNS has been unable to update the Callback URL internally

### Error scenarios
| Scenario | HTTP Status | Code |
| --- | --- | --- |
| Box ID is not a UUID | `400` | `BAD_REQUEST`
| `clientId` or `callBackUrl` is missing or invalid in request body | `400` | `INVALID_REQUEST_PAYLOAD`
| Supplied `clientId` does not match that associated with box | `401` | `UNAUTHORIZED`
| Box does not exist | `404` | `BOX_NOT_FOUND`

## `POST /box/:boxId/notifications`

Create a notification

This endpoint is restricted, only a allowlist of services can access it.

N.B. Maximum payload (request body) size currently supported is 100K.

### Path parameters
| Name | Description |
| --- | --- |
| `boxId` | Unique identifier for a box. Eg `1c5b9365-18a6-55a5-99c9-83a091ac7f26` |

### Request headers
| Name | Description |
| --- | --- |
| `Content-Type` | Either `application/json` or `application/xml` |
| `User-Agent` | User agent that identifies the calling service | 

### Request
The request body can be any JSON or XML (must match the `Content-type` header). The request body is the notification 
message that can be retrieved.

Example 1:
```
{
    "foo": "bar", 
}
```

Example 2:
```
<foo>bar</foo>
```

### Response
HTTP Status: `201`
```
{
    "notificationId": "1c5b9365-18a6-55a5-99c9-83a091ac7f26"
}
```

### Error scenarios
| Scenario | HTTP Status | Code |
| --- | --- | --- |
| Box ID is not a UUID | `400` | `BAD_REQUEST`
| Request body does not match the Content-Type header | `400` | `INVALID_REQUEST_PAYLOAD` |
| Access denied, service is not allowlisted | `403` | `FORBIDDEN`
| Box does not exist | `404` | `BOX_NOT_FOUND`
| Request is too large | `413` |

## `GET /box/:boxId/notifications`

**This endpoint is exposed through the API Platform see documentation on https://developer.qa.tax.service.gov.uk/**

Get a list of notifications that have been sent to a box

### Path parameters
| Name | Description |
| --- | --- |
| `boxId` | Unique identifier for a box. Eg `1c5b9365-18a6-55a5-99c9-83a091ac7f26` |

### Query parameters
| Name | Description |
| --- | --- |
| `status` (optional) | Only return notifications with this status. One of `PENDING`, `FAILED` or `ACKNOWLEDGED` |
| `fromDate` (optional)| Only return notifications created after this UTC datetime. ISO-8601 format. Eg `2020-06-03T14:20:54.987` |
| `toDate` (optional)| Only return notifications created before this UTC datetime. ISO-8601 format. Eg `2020-06-03T14:20:54.123` |

### Request headers
| Name | Description |
| --- | --- |
| `Authorization` | A valid _auth_ bearer token |
| `Accept` | Standard API Platform Accept header. `application/vnd.hmrc.1.0+json` | 

### Response
HTTP Status: `200`
```
[
    {
        "notificationId":"4e57c65a-f687-442c-b695-f635d5d2e856",
        "boxId":"7fe732c9-af27-4a94-973d-5c60d0a133d8",
        "messageContentType":"application/json",
        "message":"{\"test\": \"hello\"}",
        "status":"ACKNOWLEDGED",
        "createdDateTime":"2020-06-03T14:14:54.108+0000"
    },
    {
        "notificationId":"583b6394-49ea-4a8e-8d66-c3a8f4b920a3",
        "boxId":"7fe732c9-af27-4a94-973d-5c60d0a133d8",
        "messageContentType":"application/xml",
        "message":"<root>XXX</root>",
        "status":"PENDING",
        "createdDateTime":"2020-06-03T14:29:10.049+0000"
    }
]
```
| Name | Description |
| --- | --- |
| `notificationId` | Unique identifier for a notification |
| `boxId` | Unique identified for a box that the notification was sent to |
| `messageContentType` | Content type of the message, either `application/json` or `application/xml` |
| `message` | The notification message. JSON or XML as defined by messageContentType. If this is JSON then it will have been escaped. For details about the structure of this data consult the documentation for the HMRC API that created the notification
| `status` | Status of the notification. `PENDING`, `ACKNOWLEDGED`, `FAILED` |
| `createdDateTime` | ISO-8601 UTC date and time that the notification was created|

### Error scenarios
| Scenario | HTTP Status | Code |
| --- | --- | --- |
| Box ID is not a UUID | `400` | `BAD_REQUEST`
| Query parameters are invalid | `400` | `INVALID_REQUEST_PAYLOAD` |
| Access denied, The Developer Hub Client ID in the _auth_ bearer token does not have access to this box | `403` | `FORBIDDEN`
| Box does not exist | `404` | `BOX_NOT_FOUND`
| The accept header is missing or invalid | `406` | `ACCEPT_HEADER_INVALID`

## `PUT /cmb/box`
Create a new client managed box

### Request headers
| Name | Description |
| --- | --- |
| `Content-Type` | `application/json`
| `Accept Header` | `application/vnd.hmrc.1.0+json`
| `Authorisation` | `requires An OAuth 2.0 Bearer Token with the write:ppns-boxes scope`

### Request
```
{
   "boxName": "My first box"
}
```
| Name | Description |
| --- | --- |
| `boxName` | The name of the box to create |


### Response
HTTP Status: `201` if the box is created
```
{
    "boxId": "105ca34d-7a45-4df4-9fcf-9685b53799ab"
}
```

HTTP Status: `200` if the box already exists
```
{
    "boxId": "105ca34d-7a45-4df4-9fcf-9685b53799ab"
}
```

### Error scenarios
| Scenario | HTTP Status | Code |
| --- | --- | --- |
| Provided an invalid field value in request body | `400` | `INVALID_REQUEST_PAYLOAD`
| Invalid or expired bearer token | `401` | `UNAUTHORIZED`
| Provided a valid bearer token which belongs to a different client ID | `403` | `FORBIDDEN`
| Generated a bearer token with an invalid scope | `403` | `INVALID_SCOPE`
| Called the Create CMB endpoint with an incorrect accept header version  | `404` | `MATCHING_RESOURCE_NOT_FOUND`
| Called the Create CMB endpoint with an invalid or missing accept header | `406` | `ACCEPT_HEADER_INVALID`
| Missing or Invalid Content Type Header | `415` | `BAD_REQUEST`

## `GET /cmb/box`
Retrieve a list of all the boxes belonging to a specific Client ID which is passed in via auth

### Request headers
| Name | Description |
| --- | --- |
| `Accept Header` | `application/vnd.hmrc.1.0+json`
| `Authorisation` | `requires An OAuth 2.0 Bearer Token with the write:ppns-boxes scope`

### Response
HTTP Status: `200` list all boxes endpoint for a specific client ID
```
[
   {
      "boxId":"f2a14c7c-82da-4118-a09f-769580f7a5ec",
      "boxName":"DEFAULT",
      "boxCreator":{
         "clientId":"P7JXjYo6Wn13k3l5SDBlV2Qgimsu"
      },
      "applicationId":"6722217f-25ce-423a-93cd-4d3d0c8af11b",
      "subscriber":{
         "callBackUrl":"",
         "subscribedDateTime":"2022-06-15T14:24:24.385+0000",
         "subscriptionType":"API_PULL_SUBSCRIBER"
      },
      "clientManaged":false
   },
   {
      "boxId":"aca044b1-cd06-44a7-bd6b-bd7c58ea9ad4",
      "boxName":"My First Client Managed Box",
      "boxCreator":{
         "clientId":"P7JXjYo6Wn13k3l5SDBlV2Qgimsu"
      },
      "applicationId":"6722217f-25ce-423a-93cd-4d3d0c8af11b",
      "clientManaged":true
   }
 ]
```
| Name | Description |
| --- | --- |
| `boxId` | Identifier for a box
| `boxName` | The boxName will be returned as "DEFAULT" if clientManaged is false
| `boxCreator.clientId` | Developer Hub Application Client ID, that created and has access to this box
| `subscriber` | Details of the subscriber to this box |
| `subscriber.subscribedDateTime` | ISO-8601 UTC date and time that the subscription was created |
| `subscriber.callBackUrl` | The URL of the endpoint where push notifications will be sent |
| `subscriber.subscriptionType` | The type of subscriber. Currently only `API_PUSH_SUBSCRIBER` is supported |
| `clientManaged` | Boolean value to show if the box is client managed |

HTTP Status: `200` list all boxes endpoint for a specific client ID which has no boxes
```
[]
```

### Error scenarios
| Scenario | HTTP Status | Code |
| --- | --- | --- |
| Invalid or expired bearer token | `401` | `UNAUTHORIZED`
| Called the endpoint with an invalid or missing accept header | `406` | `ACCEPT_HEADER_INVALID`

## `DELETE /cmb/box/:boxId`
Delete a client managed box

### Request headers
| Name | Description |
| --- | --- |
| `Content-Type` | `application/json`
| `Accept Header` | `application/vnd.hmrc.1.0+json`
| `Authorisation` | `requires An OAuth 2.0 Bearer Token with the write:ppns-boxes scope`

### Path parameters
| Name | Description |
| --- | --- |
| `boxId` | Unique identifier for a box |


### Response
HTTP Status: `204` if the box is deleted

### Error scenarios
| Scenario | HTTP Status | Code |
| --- | --- | --- |
| Invalid or expired bearer token | `401` | `UNAUTHORIZED`
| Response when attempting to delete a box which belongs to a different client ID  | `403` | `FORBIDDEN`
| Attempt to delete a default box should not be allowed for example a default box | `403` | `FORBIDDEN`
| Generated a bearer token with an invalid scope | `403` | `INVALID_SCOPE`
| Response when attempting to delete a box with an ID that does not exist | `404` | `BOX_NOT_FOUND`
| Called the Delete CMB endpoint with an incorrect accept header version  | `404` | `MATCHING_RESOURCE_NOT_FOUND`
| Called the Create CMB endpoint with an invalid or missing accept header | `406` | `ACCEPT_HEADER_INVALID`

# Run locally and call the API locally

If you need to call any of the endpoints exposed over the API Platform, you need to pass in an valid bearer token for the application restricted endpoints:

1. Get an API Platform bearer token

```
curl --location --request POST 'http://localhost:9613/token' \
--header 'content-type: application/x-www-form-urlencoded' \
--data-urlencode 'client_secret=???????' \
--data-urlencode 'client_id=????????' \
--data-urlencode 'grant_type=client_credentials'
```

(Replacing the ```client_id``` & ```client_secret``` with ones from you local setup)

2. Get the internal bearer token from the API platform's external bearer token:

```
 mongo localhost/third-party-delegated-authority --eval "db.delegatedAuthority.find({
    'token.accessToken' : '???????' 
 },{
     _id: 0,  
     authBearerToken: 1   
 })"
 ```
 (Replacing the ```accessToken``` with the one obtained in step 1)

3. You can then call application restricted endpoints in the PPNS API:

```
curl --location --request GET 'http://localhost:6701/box/{box-id}/notifications' \
--header 'Content-Type: application/json' \
--header 'Accept: application/vnd.hmrc.1.0+json' \
--header 'Authorization: Bearer ??????????'
```
(Replacing the ```box-id``` with a valid local box-id and the ```Authorization: Bearer``` with the internal bearer token obtained in step 2)

### License
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
