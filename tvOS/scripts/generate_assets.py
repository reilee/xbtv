#!/usr/bin/env python3
"""Rebuild the original vector-style TV mark as tvOS layered assets (Pillow required)."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1] / 'XBTV/Resources/Assets.xcassets'
info = {'author': 'xcode', 'version': 1}
def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')

def artwork(size, foreground=False):
    w,h = size
    image = Image.new('RGBA', size, (0,0,0,0))
    draw = ImageDraw.Draw(image)
    if not foreground:
        for y in range(h):
            t=y/h
            draw.line([(0,y),(w,y)], fill=(int(18+12*t), int(28+20*t), int(48+30*t),255))
    else:
        box=(w*.26,h*.22,w*.74,h*.73)
        draw.rounded_rectangle(box, radius=int(h*.09), outline=(230,242,255,255), width=max(3,int(h*.022)))
        draw.polygon([(w*.46,h*.34),(w*.46,h*.61),(w*.59,h*.475)],fill=(130,207,255,255))
        draw.line([(w*.43,h*.82),(w*.57,h*.82)],fill=(230,242,255,255),width=max(3,int(h*.022)))
    return image
write(root/'Contents.json', {'info':info})
brand=root/'App Icon & Top Shelf Image.brandassets'
assets=[]
for name,role,size in [('App Icon','primary-app-icon',(400,240)),('App Icon Large','primary-app-icon',(1280,768))]:
    stack=brand/(name+'.imagestack')
    assets.append({'idiom':'tv','size':f'{size[0]}x{size[1]}','role':role,'filename':stack.name})
    layers=[]
    for layer,front in [('Front',True),('Back',False)]:
        folder=stack/(layer+'.imagestacklayer')
        layers.append({'filename':folder.name})
        write(folder/'Contents.json',{'info':info})
        content=folder/'Content.imageset'
        images=[]
        for scale in [1,2]:
            filename=f'icon@{scale}x.png'
            images.append({'idiom':'tv','filename':filename,'scale':f'{scale}x'})
            content.mkdir(parents=True,exist_ok=True)
            artwork((size[0]*scale,size[1]*scale),front).save(content/filename)
        write(content/'Contents.json',{'images':images,'info':info})
    write(stack/'Contents.json',{'layers':layers,'info':info})
for name,role,size in [('Top Shelf Image','top-shelf-image',(1920,720)),('Top Shelf Image Wide','top-shelf-image-wide',(2320,720))]:
    folder=brand/(name+'.imageset')
    assets.append({'idiom':'tv','role':role,'size':f'{size[0]}x{size[1]}','filename':folder.name})
    images=[]
    for scale in [1,2]:
        filename=f'shelf@{scale}x.png';folder.mkdir(parents=True,exist_ok=True)
        image=artwork((size[0]*scale,size[1]*scale));image.alpha_composite(artwork(image.size,True));image.convert('RGB').save(folder/filename)
        images.append({'idiom':'tv','filename':filename,'scale':f'{scale}x'})
    write(folder/'Contents.json',{'images':images,'info':info})
write(brand/'Contents.json',{'assets':assets,'info':info})
