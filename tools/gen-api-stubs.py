import sys, os, xml.etree.ElementTree as ET
out = sys.argv[1]
classes = {}
for f in sys.argv[2:]:
    root = ET.parse(f).getroot()
    for el in root:
        classes[el.get('name')] = el
def ctor_params(name):
    el = classes.get(name)
    if el is None: return None
    cs = el.findall('constructor')
    if not cs: return []  # implicit/private none
    best = min(cs, key=lambda c: len(c.findall('parameter')))
    return [p.get('type') for p in best.findall('parameter')]
def dflt(t):
    if t in ('int','long','short','byte','char','float','double'): return '(%s)0' % t
    if t == 'boolean': return 'false'
    return '(%s)null' % t
def lit(t, v):
    if t == 'java.lang.String': return '"' + v.replace('\\','\\\\').replace('"','\\"') + '"'
    if t == 'long': return v + 'L'
    if t == 'float': return v + 'f'
    if t == 'double': return v + 'd' if v not in ('NaN','Infinity','-Infinity') else {'NaN':'0.0d/0.0','Infinity':'1.0d/0.0','-Infinity':'-1.0d/0.0'}[v]
    if t == 'char': return '(char)%d' % (ord(v) if not v.isdigit() or len(v)==1 and False else int(v)) if False else "(char)" + (v if v.lstrip('-').isdigit() else str(ord(v)))
    if t == 'boolean': return v
    return '(%s)%s' % (t, v) if t in ('byte','short') else v
def ret_body(r):
    return 'throw new RuntimeException();'
for name, el in classes.items():
    isif = el.tag == 'interface'
    pkg, cls = name.rsplit('.', 1)
    mods = el.get('modifiers','public')
    impls = [i.get('name') for i in el.findall('implements/interface')]
    ext = el.get('extends')
    L = ['package %s;' % pkg]
    if isif:
        mods = mods.replace('abstract','').replace('interface','')
        h = '%s interface %s' % (mods.strip(), cls)
        if impls: h += ' extends ' + ', '.join(impls)
    else:
        h = '%s class %s' % (mods, cls)
        if ext and ext != 'java.lang.Object' or (ext and name != 'java.lang.Object'):
            if ext: h += ' extends ' + ext
        if impls: h += ' implements ' + ', '.join(impls)
    L.append(h + ' {')
    for fe in el.findall('field'):
        t = fe.get('type'); m = fe.get('modifiers',''); cv = fe.get('constant-value')
        if cv is not None:
            L.append('  %s %s %s = %s;' % (m, t, fe.get('name'), lit(t, cv)))
        else:
            m2 = m.replace('final','') if not isif else m
            if isif: L.append('  %s %s %s = %s;' % (m, t, fe.get('name'), dflt(t)))
            else: L.append('  %s %s %s;' % (m2, t, fe.get('name')))
    def sig(e):
        ps = ', '.join('%s p%d' % (p.get('type'), i) for i, p in enumerate(e.findall('parameter')))
        ex = [x.get('name') for x in e.findall('exception')]
        return '(' + ps + ')' + (' throws ' + ', '.join(ex) if ex else '')
    sup = ext if ext else None
    cs = el.findall('constructor')
    def supercall():
        if name == 'java.lang.Object' or sup is None: return ''
        ps = ctor_params(sup)
        if not ps: return ''
        return 'super(' + ', '.join(dflt(t) for t in ps) + ');'
    if not isif:
        for c in cs:
            L.append('  %s %s%s { %s }' % (c.get('modifiers',''), cls, sig(c), supercall()))
        if not cs:
            L.append('  private %s() { %s }' % (cls, supercall()))
    for me in el.findall('method'):
        m = me.get('modifiers',''); r = me.get('return','void')
        if isif:
            L.append('  %s %s%s;' % (r, me.get('name'), sig(me)))
        elif 'abstract' in m:
            L.append('  %s %s %s%s;' % (m, r, me.get('name'), sig(me)))
        else:
            m = m.replace('native','').replace('synchronized','')
            L.append('  %s %s %s%s { %s }' % (m, r, me.get('name'), sig(me), ret_body(r)))
    L.append('}')
    d = os.path.join(out, *pkg.split('.')); os.makedirs(d, exist_ok=True)
    open(os.path.join(d, cls + '.java'), 'w').write('\n'.join(L) + '\n')
print(len(classes))
