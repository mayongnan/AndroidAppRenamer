import os, sys
import shutil
import subprocess
import logging
import traceback

Tool_7z = os.path.join(os.path.dirname(__file__), "7z.exe")
APKSIGNER = os.path.join(os.path.dirname(__file__), "apksigner.bat")
KEYSTORE = os.path.join(os.path.dirname(__file__), "yon-test.jks")
ZIPALIGN = os.path.join(os.path.dirname(__file__), "zipalign.exe")
RENAME_JAR = os.path.join(os.path.dirname(__file__), "AndroidAppRenamer.arsc_decoder.jar")


def rename_app(origin_file_path, rename_res_name):
    try:
        logging.getLogger().setLevel(logging.INFO)
        origin_dir_path = os.path.dirname(origin_file_path)
        file_path = os.path.join(origin_dir_path, 'rename-out', os.path.basename(origin_file_path))

        dir_path = os.path.dirname(file_path)
        if not os.path.exists(dir_path):
            os.makedirs(dir_path)
        del_list = os.listdir(dir_path)
        if len(del_list) > 0:
            for f in del_list:
                del_file_path = os.path.join(dir_path, f)
                if os.path.isfile(del_file_path):
                    os.remove(del_file_path)
                elif os.path.isdir(del_file_path):
                    shutil.rmtree(del_file_path)
        # 复制原apk
        shutil.copy(origin_file_path, file_path)
        # 获取resources.arsc文件
        arsc_file = 'resources.arsc'
        unzip_arsc_cmd = r"%s e %s -o%s %s" % (Tool_7z, file_path, dir_path, arsc_file)
        logging.info(unzip_arsc_cmd)
        content_bytes = subprocess.check_output(unzip_arsc_cmd, cwd=dir_path)
        content = content_bytes.decode()
        logging.info(content)
        # 执行jar文件命令进行arsc文件修改
        arsc_unzip_path = os.path.join(dir_path, arsc_file)
        arsc_temp_unzip_path = os.path.join(dir_path, "arsc-out")
        if not os.path.exists(arsc_temp_unzip_path):
            os.makedirs(arsc_temp_unzip_path)

        arsc_temp_unzip_path = os.path.join(arsc_temp_unzip_path, arsc_file)
        jar_cmd = r"java -jar %s %s %s %s" % (RENAME_JAR, arsc_unzip_path, arsc_temp_unzip_path, rename_res_name)
        logging.info(jar_cmd)
        content_bytes = subprocess.check_output(jar_cmd, cwd=dir_path)
        content = content_bytes.decode("utf8", "ignore")
        logging.info(content)
        # 重新打包新的apk
        # 7z 压入修改之后的arsc文件
        write_arsc_cmd = r"%s a -tzip %s %s" % (Tool_7z, file_path, arsc_temp_unzip_path)
        logging.info(write_arsc_cmd)
        content_bytes = subprocess.check_output(write_arsc_cmd, cwd=dir_path)
        content = content_bytes.decode()
        logging.info(content)

        # 使用 zipalign 对齐未签名的 APK
        temp_file = os.path.join(origin_dir_path, 'rename-out', "dest.apk")
        zip_align_cmd = r'%s -v -p 4 %s %s' % (ZIPALIGN, file_path, temp_file)
        logging.info(zip_align_cmd)
        content_bytes = subprocess.check_output(zip_align_cmd)
        content = content_bytes.decode()
        logging.info("zip align over...")

        os.remove(file_path)
        os.rename(temp_file, file_path)
        # 重新签名
        re_sign_cmd = r'%s sign -verbose --ks %s --ks-key-alias yon-test ' \
                      r'--ks-pass pass:123456 --key-pass pass:123456 %s' \
                      % (APKSIGNER, KEYSTORE, file_path)
        logging.info(re_sign_cmd)
        content_bytes = subprocess.check_output(re_sign_cmd)
        content = content_bytes.decode()
        logging.info(content)
        return True
    except all:
        logging.error(traceback.format_exc())
        return False


if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("please provide an apk file and rename string resource name")
    else:
        apk_path = sys.argv[1]
        rename_res_name = sys.argv[2]
        rename_app(apk_path, rename_res_name)
