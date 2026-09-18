#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <espeak-ng/speak_lib.h>

#define CLAUSE_INTONATION_FULL_STOP 0x00000000
#define CLAUSE_INTONATION_COMMA 0x00001000
#define CLAUSE_INTONATION_QUESTION 0x00002000
#define CLAUSE_INTONATION_EXCLAMATION 0x00003000
#define CLAUSE_TYPE_SENTENCE 0x00080000
#define CLAUSE_PERIOD (40 | CLAUSE_INTONATION_FULL_STOP | CLAUSE_TYPE_SENTENCE)
#define CLAUSE_COMMA (20 | CLAUSE_INTONATION_COMMA)
#define CLAUSE_QUESTION (40 | CLAUSE_INTONATION_QUESTION | CLAUSE_TYPE_SENTENCE)
#define CLAUSE_EXCLAMATION (45 | CLAUSE_INTONATION_EXCLAMATION | CLAUSE_TYPE_SENTENCE)
#define CLAUSE_COLON (30 | CLAUSE_INTONATION_FULL_STOP)
#define CLAUSE_SEMICOLON (30 | CLAUSE_INTONATION_COMMA)

static int initialized = 0;

static const char *terminator_text(int value) {
  value &= 0x000fffff;
  if (value == CLAUSE_PERIOD) return ".";
  if (value == CLAUSE_QUESTION) return "?";
  if (value == CLAUSE_EXCLAMATION) return "!";
  if (value == CLAUSE_COMMA) return ",";
  if (value == CLAUSE_COLON) return ":";
  if (value == CLAUSE_SEMICOLON) return ";";
  return "";
}

static int append_text(char **buffer, size_t *length, size_t *capacity, const char *text) {
  size_t add = strlen(text);
  if (*length + add + 1 > *capacity) {
    size_t next = *capacity;
    while (*length + add + 1 > next) next *= 2;
    char *grown = (char *)realloc(*buffer, next);
    if (grown == NULL) return 0;
    *buffer = grown;
    *capacity = next;
  }
  memcpy(*buffer + *length, text, add);
  *length += add;
  (*buffer)[*length] = 0;
  return 1;
}

JNIEXPORT jboolean JNICALL
Java_com_yiwoosolution_piperprototype_EnglishEspeak_nativeInit(JNIEnv *env, jclass clazz, jstring data_path) {
  (void)env; (void)clazz;
  const char *path = (*env)->GetStringUTFChars(env, data_path, NULL);
  int result = espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, 0, path, 0);
  (*env)->ReleaseStringUTFChars(env, data_path, path);
  if (result < 0) return JNI_FALSE;
  initialized = 1;
  return espeak_SetVoiceByName("en-us") == EE_OK ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_yiwoosolution_piperprototype_EnglishEspeak_nativePhonemize(JNIEnv *env, jclass clazz, jstring input) {
  (void)clazz;
  if (!initialized || input == NULL) return NULL;
  const char *text = (*env)->GetStringUTFChars(env, input, NULL);
  const void *cursor = text;
  size_t capacity = 4096, length = 0;
  char *result = (char *)calloc(1, capacity);
  if (result == NULL) { (*env)->ReleaseStringUTFChars(env, input, text); return NULL; }
  while (cursor != NULL) {
    int terminator = 0;
    const char *phonemes = espeak_TextToPhonemesWithTerminator(&cursor, espeakCHARS_UTF8, espeakPHONEMES_IPA, &terminator);
    if (phonemes == NULL) break;
    if (!append_text(&result, &length, &capacity, phonemes) ||
        !append_text(&result, &length, &capacity, "\t") ||
        !append_text(&result, &length, &capacity, terminator_text(terminator)) ||
        !append_text(&result, &length, &capacity, "\t") ||
        !append_text(&result, &length, &capacity, (terminator & CLAUSE_TYPE_SENTENCE) ? "1\n" : "0\n")) {
      free(result); (*env)->ReleaseStringUTFChars(env, input, text); return NULL;
    }
  }
  (*env)->ReleaseStringUTFChars(env, input, text);
  jstring output = (*env)->NewStringUTF(env, result);
  free(result);
  return output;
}
