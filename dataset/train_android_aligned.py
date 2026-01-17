import tensorflow as tf
from tensorflow.keras import layers, models
import os
import shutil
from tensorflow.keras import mixed_precision
import time

gpus = tf.config.list_physical_devices('GPU')
if gpus:
    print(f"✅ 检测到 GPU: {gpus}")
else:
    print("❌ 未检测到 GPU，将使用 CPU 训练。")

if gpus:
    try:
        for gpu in gpus:
            tf.config.experimental.set_memory_growth(gpu, True)
    except RuntimeError as e:
        print(e)

        
#tf.debugging.set_log_device_placement(True) # 运行后控制台会打印每个算子的位置


# policy = mixed_precision.Policy('mixed_float16')
# mixed_precision.set_global_policy(policy)


ANDROID_ASSETS_DIR = "../app/src/main/assets"
DATASET_PATH = "dataset_final_rgb"
MODEL_SAVE_PATH = "chess_model.tflite"
MODEL_SAVE_PATH_INT8 = "chess_model_int8.tflite"
LABELS_PATH = "labels.txt"

IMAGE_SIZE = (64, 64)
BATCH_SIZE = 32
EPOCHS = 20 
SEED = 123


train_ds = tf.keras.utils.image_dataset_from_directory(
    DATASET_PATH,
    validation_split=0.2,
    subset="training",
    seed=SEED,
    color_mode='rgb',  
    image_size=IMAGE_SIZE,
    batch_size=BATCH_SIZE
)


# 打印训练集的输入尺寸
for image, _ in train_ds.take(1):  # 取一个批次
    print("Input image size:", image.shape)

val_ds = tf.keras.utils.image_dataset_from_directory(
    DATASET_PATH,
    validation_split=0.2,
    subset="validation",
    seed=SEED,
    image_size=IMAGE_SIZE,
    batch_size=BATCH_SIZE
)

class_names = train_ds.class_names
print("Classes:", class_names)


data_augmentation = tf.keras.Sequential([
    layers.RandomContrast(0.2),
    # layers.Lambda(lambda x: tf.image.random_brightness(x, max_delta=0.2)) 
])


model = models.Sequential([
    layers.InputLayer(input_shape=(64, 64, 3)),
    

    layers.Rescaling(1.0 / 255.0),  

    data_augmentation,
    

    layers.Conv2D(32, 3, activation='relu', padding='same'),
    layers.MaxPooling2D(),
    
    layers.Conv2D(64, 3, activation='relu', padding='same'),
    layers.MaxPooling2D(),
    
    layers.Conv2D(128, 3, activation='relu', padding='same'), 
    layers.MaxPooling2D(),

    layers.Flatten(),
    layers.Dense(128, activation='relu'),
    layers.Dropout(0.2), # 加入随机失活防止过拟合
    #layers.Dense(len(class_names), activation='softmax')
    # 修改最后一行
    layers.Dense(len(class_names), activation='softmax', dtype='float32')

])


train_ds = train_ds.cache().shuffle(1000).prefetch(buffer_size=tf.data.AUTOTUNE)
val_ds = val_ds.cache().prefetch(buffer_size=tf.data.AUTOTUNE)


start_time = time.time()

model.compile(
    optimizer='adam',
    loss='sparse_categorical_crossentropy',
    metrics=['accuracy']
)

model.summary()

model.fit(
    train_ds,
    validation_data=val_ds,
    epochs=EPOCHS
)

print("训练时间: %.2f 秒" % (time.time() - start_time))

converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()



with open(MODEL_SAVE_PATH, "wb") as f:
    f.write(tflite_model)


with open(LABELS_PATH, "w") as f:
    for name in class_names:
        f.write(name + "\n")

def representative_data_gen():
    # 只需要 image，不需要 label
    for images, _ in train_ds.take(100): # 100个批次通常足够了
        for img in images:
            # 增加一个维度，满足模型输入的 Batch 要求
            yield [tf.expand_dims(img, 0)]


# def representative_data_gen():
#     for image, _ in train_ds.take(1000):  # 或更多样本
#         yield [image]

# 创建模型转换器
converter = tf.lite.TFLiteConverter.from_keras_model(model)

# 设置量化优化
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.representative_dataset = representative_data_gen

# 设置量化类型为 INT8
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]

# 设置输入输出数据类型为 uint8
converter.inference_input_type = tf.uint8
converter.inference_output_type = tf.uint8

# 转换模型
tflite_model_int8 = converter.convert()
with open(MODEL_SAVE_PATH_INT8, "wb") as f:
    f.write(tflite_model_int8)



try:
    os.makedirs(ANDROID_ASSETS_DIR, exist_ok=True)
    shutil.copy(MODEL_SAVE_PATH, ANDROID_ASSETS_DIR)
    shutil.copy(MODEL_SAVE_PATH_INT8, ANDROID_ASSETS_DIR)
    shutil.copy(LABELS_PATH, ANDROID_ASSETS_DIR)
    

    tflite_exists = os.path.exists(os.path.join(ANDROID_ASSETS_DIR, MODEL_SAVE_PATH))
    labels_exists = os.path.exists(os.path.join(ANDROID_ASSETS_DIR, LABELS_PATH))
    
    if tflite_exists and labels_exists:
        print(f"\n✅ 成功：模型和标签已导出并拷贝至: {ANDROID_ASSETS_DIR}")
    else:
        print("\n❌ 错误：文件拷贝失败，请检查路径。")
except Exception as e:
    print(f"\n❌ 拷贝过程中出现异常: {e}")

