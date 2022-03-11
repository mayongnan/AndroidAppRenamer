# AndroidAppRenamer
通过使用apk中预定义的string资源名称，对app进行重新命名


## 使用方法

```
python app_rename.py [apk path] [rename string resource]
eg: python app_rename.py test.apk app_name2
```

## 设计思想

对apk中arsc文件进行编辑，将app_name的值修改为指定的资源名称（使用了较为保守的修改方案，仅支持修改为apk中已有资源名称对应的值）


