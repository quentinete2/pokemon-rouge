package org.example;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;

public class PokemonGame extends JPanel implements KeyListener, ActionListener {
    private static final int W = 640, H = 480, TILE = 32, MAP_W = 30, MAP_H = 24;
    private static final Path SAVE = Path.of(System.getProperty("user.home"), ".pokemon-rouge-save.properties");

    private enum Screen { TITLE, WORLD, DIALOG, BATTLE, MENU, POKEDEX }
    private enum Tile { GRASS, TALL, PATH, WATER, TREE, WALL, DOOR, ROCK, FLOOR, SIGN, SHOP, ARENA, FENCE, FLOWER, CAVE, BUILDING }
    private enum Direction { UP(0,-1), DOWN(0,1), LEFT(-1,0), RIGHT(1,0);
        final int dx, dy;
        Direction(int dx, int dy) { this.dx = dx; this.dy = dy; }
    }

    private Screen screen = Screen.TITLE;
    private final Tile[][] map = new Tile[MAP_H][MAP_W];
    private int px = 11, py = 16;
    private float displayX = 11*TILE, displayY = 16*TILE;
    private Direction heldDirection;
    private boolean walking;
    private Direction facing = Direction.DOWN;
    private final Random random = new Random();
    private final Timer timer = new Timer(16, this);
    private String dialog = "";
    private int dialogChars;
    private long dialogTick;
    private int menuIndex, battleIndex;
    private final Monster player = new Monster("Pikachu", 25, 14, 9, 5);
    private Monster wild;
    private int potions = 3, balls = 5, coins = 500, badges;
    private final boolean[] seen = new boolean[12];
    private final boolean[] caught = new boolean[12];
    private int cameraX, cameraY;
    private boolean arenaDefeated;
    private boolean trainerBattle;
    private int healItemsBought;
    private int currentRoute;
    private final Map<Integer, BufferedImage[]> pokemonSprites = new HashMap<>();
    private final Map<Integer, BufferedImage> pokemonBackSprites = new HashMap<>();

    public PokemonGame() {
        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        addKeyListener(this);
        makeMap();
        px = 11;
        py = 16;
        displayX = px*TILE;
        displayY = py*TILE;
        seen[0] = true;
        caught[0] = true;
        preloadPokemonSprites();
        timer.start();
    }

    private void preloadPokemonSprites() {
        int[] ids={25,1,4,7,19,102,16,63,23,29,150,92};
        for(int id:ids) {
            BufferedImage[] frames=new BufferedImage[2];
            frames[0]=loadSprite("assets/generation-4/pokemon/main-sprites/diamond-pearl/"+id+".png");
            frames[1]=loadSprite("assets/generation-4/pokemon/main-sprites/diamond-pearl/frame2/"+id+".png");
            if(frames[1]==null)frames[1]=frames[0];
            pokemonSprites.put(id,frames);
            pokemonBackSprites.put(id,loadSprite("assets/generation-4/pokemon/main-sprites/diamond-pearl/back/"+id+".png"));
        }
    }

    private BufferedImage loadSprite(String path) {
        try{return ImageIO.read(new File(path));}
        catch(IOException ignored){return null;}
    }

    private BufferedImage pokemonSprite(Monster monster,boolean playerSide) {
        int id=pokedexIndex(monster.name);
        int[] ids={25,1,4,7,19,102,16,63,23,29,150,92};
        int nationalId=ids[id];
        if(playerSide){
            BufferedImage back=pokemonBackSprites.get(nationalId);
            if(back!=null)return back;
        }
        BufferedImage[] frames=pokemonSprites.get(nationalId);
        if(frames==null)return null;
        int frame=(int)(System.currentTimeMillis()/420)%frames.length;
        return frames[frame];
    }

    private void makeMap() {
        for (int y = 0; y < MAP_H; y++) {
            for (int x = 0; x < MAP_W; x++) {
                if (x == 0 || y == 0 || x == MAP_W - 1 || y == MAP_H - 1) map[y][x] = Tile.TREE;
                else if (x >= 10 && x <= 12) map[y][x] = Tile.PATH;
                else if (y >= 15 && y <= 17) map[y][x] = Tile.PATH;
                else if (y >= 4 && y <= 7 && x >= 20 && x <= 22) map[y][x] = Tile.PATH;
                else if (y >= 1 && y <= 3 && x >= 13 && x <= 17) map[y][x] = Tile.PATH;
                else if ((y >= 3 && y <= 5 && x >= 2 && x <= 7)
                        || (y >= 19 && y <= 22 && x >= 5 && x <= 18)
                        || (y >= 8 && y <= 12 && x >= 15 && x <= 18)) map[y][x] = Tile.TALL;
                else if (y >= 8 && y <= 10 && x >= 2 && x <= 5) map[y][x] = Tile.WATER;
                else if (y == 13 && x >= 3 && x <= 8) map[y][x] = Tile.FENCE;
                else if ((y==14||y==15)&&(x>=4&&x<=7))map[y][x]=Tile.FLOWER;
                else map[y][x] = Tile.GRASS;
            }
        }
        // Bourg Palette
        map[18][5] = Tile.DOOR; map[18][6] = Tile.DOOR;
        map[19][5] = Tile.WALL; map[19][6] = Tile.WALL;
        // Jadielle
        map[10][19] = Tile.SHOP; map[10][20] = Tile.SHOP;
        map[11][19] = Tile.DOOR; map[11][20] = Tile.DOOR;
        // Arène
        map[5][24] = Tile.ARENA; map[5][25] = Tile.ARENA;
        map[6][24] = Tile.DOOR; map[6][25] = Tile.DOOR;
        map[7][24] = Tile.WALL; map[7][25] = Tile.WALL;
        // Caverne Sillabe
        for(int y=18;y<=21;y++)for(int x=21;x<=25;x++)map[y][x]=Tile.CAVE;
        map[21][23]=Tile.DOOR;
        map[21][24]=Tile.DOOR;
        // Foret de Jade
        for(int y=2;y<=7;y++)for(int x=2;x<=8;x++)if((x+y)%3==0)map[y][x]=Tile.TREE;
        // Decors des rues
        for(int y=11;y<=13;y++){map[y][3]=Tile.BUILDING;map[y][4]=Tile.BUILDING;}
        map[14][11] = Tile.SIGN;
        map[16][8] = Tile.ROCK;
        map[16][9] = Tile.ROCK;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        switch (screen) {
            case TITLE -> drawTitle(g);
            case WORLD -> { drawWorld(g); drawHud(g); }
            case DIALOG -> { drawWorld(g); drawDialog(g); }
            case BATTLE -> drawBattle(g);
            case MENU -> { drawWorld(g); drawMenu(g); }
            case POKEDEX -> drawPokedex(g);
        }
    }

    private void drawTitle(Graphics2D g) {
        g.setColor(new Color(20,24,36));
        g.fillRect(0,0,W,H);
        g.setColor(new Color(255,70,70));
        g.fillOval(180,55,280,280);
        g.setColor(new Color(245,245,220));
        g.fillArc(180,55,280,280,180,180);
        g.setColor(new Color(20,24,36));
        g.fillRect(180,194,280,24);
        g.setColor(Color.WHITE);
        g.fillOval(272,147,96,96);
        g.setColor(new Color(25,25,25));
        g.fillOval(292,167,56,56);
        g.setColor(Color.WHITE);
        g.fillOval(306,181,28,28);
        g.setFont(new Font("Consolas", Font.BOLD, 48));
        g.setColor(Color.YELLOW);
        g.drawString("POKEMON",208,370);
        g.setFont(new Font("Consolas", Font.BOLD, 38));
        g.setColor(Color.WHITE);
        g.drawString("ROUGE",251,414);
        g.setFont(new Font("Consolas", Font.BOLD,18));
        if (System.currentTimeMillis()/500%2==0) g.drawString("Appuyez sur Entree",225,455);
    }

    private void drawWorld(Graphics2D g) {
        cameraX = Math.max(0, Math.min(MAP_W*TILE-W, (int)displayX-W/2));
        cameraY = Math.max(0, Math.min(MAP_H*TILE-H, (int)displayY-H/2));
        g.translate(-cameraX, -cameraY);
        int startX = cameraX/TILE, endX = Math.min(MAP_W, startX+W/TILE+2);
        int startY = cameraY/TILE, endY = Math.min(MAP_H, startY+H/TILE+2);
        for (int y=startY;y<endY;y++) for (int x=startX;x<endX;x++) drawTile(g,map[y][x],x*TILE,y*TILE);
        drawPerson(g,10,16,new Color(55,95,180));
        drawPerson(g,19,10,new Color(210,150,70));
        drawPerson(g,24,8,new Color(120,55,55));
        drawSign(g,14,14,"ROUTE 01");
        drawPlayer(g);
        g.translate(cameraX, cameraY);
    }

    private void drawTile(Graphics2D g, Tile t, int x, int y) {
        switch(t) {
            case GRASS -> { g.setColor(new Color(104,164,73)); g.fillRect(x,y,TILE,TILE); }
            case TALL -> {
                g.setColor(new Color(58,142,55)); g.fillRect(x,y,TILE,TILE);
                g.setColor(new Color(124,196,68));
                for(int i=0;i<4;i++) g.fillRect(x+i*8+2,y+6+(i%2)*5,4,20);
            }
            case PATH -> { g.setColor(new Color(210,194,138)); g.fillRect(x,y,TILE,TILE); g.setColor(new Color(185,170,120)); g.fillRect(x+14,y+14,3,3); }
            case WATER -> { g.setColor(new Color(78,158,210)); g.fillRect(x,y,TILE,TILE); g.setColor(new Color(160,220,245)); g.fillRect(x+4,y+8,18,3); g.fillRect(x+12,y+20,14,3); }
            case TREE -> { g.setColor(new Color(76,55,32)); g.fillRect(x+12,y+16,8,16); g.setColor(new Color(35,104,48)); g.fillRect(x+2,y+2,28,22); g.setColor(new Color(78,145,61)); g.fillRect(x+6,y+6,14,12); }
            case WALL -> { g.setColor(new Color(160,90,72)); g.fillRect(x,y,TILE,TILE); g.setColor(new Color(205,165,120)); g.fillRect(x+4,y+4,24,20); }
            case DOOR -> { g.setColor(new Color(130,72,48)); g.fillRect(x,y,TILE,TILE); g.setColor(new Color(90,45,32)); g.fillRect(x+4,y+4,24,28); g.setColor(new Color(230,205,120)); g.fillOval(x+21,y+18,4,4); }
            case ROCK -> { g.setColor(new Color(125,125,118)); g.fillRect(x+4,y+7,24,21); g.setColor(new Color(168,168,158)); g.fillRect(x+8,y+3,16,12); }
            case FLOOR -> { g.setColor(new Color(220,205,170)); g.fillRect(x,y,TILE,TILE); }
            case SIGN -> { g.setColor(new Color(115,75,45)); g.fillRect(x+13,y+14,6,18); g.setColor(new Color(235,220,150)); g.fillRect(x+4,y+5,24,14); }
            case SHOP -> { g.setColor(new Color(155,95,65)); g.fillRect(x,y,TILE,TILE); g.setColor(new Color(230,200,120)); g.fillRect(x+4,y+4,24,24); }
            case ARENA -> { g.setColor(new Color(170,120,70)); g.fillRect(x,y,TILE,TILE); g.setColor(new Color(210,180,100)); g.fillRect(x+3,y+3,26,26); }
            case FENCE -> { g.setColor(new Color(104,164,73)); g.fillRect(x,y,TILE,TILE); g.setColor(new Color(238,230,190)); g.fillRect(x,y+12,TILE,5); g.fillRect(x+6,y+4,4,24); g.fillRect(x+22,y+4,4,24); }
            case FLOWER -> { g.setColor(new Color(104,164,73)); g.fillRect(x,y,TILE,TILE); g.setColor(new Color(245,175,205)); g.fillOval(x+5,y+6,7,7); g.fillOval(x+18,y+16,7,7); g.setColor(new Color(255,220,80)); g.fillOval(x+8,y+9,3,3); g.fillOval(x+21,y+19,3,3); }
            case CAVE -> { g.setColor(new Color(105,98,88)); g.fillRect(x,y,TILE,TILE); g.setColor(new Color(55,55,52)); g.fillOval(x+3,y+7,26,22); g.setColor(new Color(20,20,22)); g.fillRect(x+9,y+17,14,12); }
            case BUILDING -> { g.setColor(new Color(90,130,165)); g.fillRect(x,y,TILE,TILE); g.setColor(new Color(220,220,190)); g.fillRect(x+3,y+4,26,24); g.setColor(new Color(70,120,175)); g.fillRect(x+7,y+8,18,10); }
        }
    }

    private void drawSign(Graphics2D g,int tx,int ty,String text) {
        int x=tx*TILE,y=ty*TILE;
        g.setColor(new Color(115,75,45)); g.fillRect(x+13,y+14,6,18);
        g.setColor(new Color(235,220,150)); g.fillRect(x+4,y+5,24,14);
        g.setColor(new Color(60,50,40)); g.setFont(new Font("Consolas",Font.BOLD,9)); g.drawString(text,x+7,y+15);
    }

    private void drawPlayer(Graphics2D g) {
        int x=(int)displayX,y=(int)displayY;
        g.setColor(new Color(230,195,145)); g.fillRect(x+9,y+5,14,10);
        g.setColor(new Color(210,55,55)); g.fillRect(x+7,y+14,18,14);
        g.setColor(new Color(35,70,150)); g.fillRect(x+8,y+27,7,5); g.fillRect(x+17,y+27,7,5);
        g.setColor(Color.RED); g.fillRect(x+6,y+2,20,7);
        g.setColor(new Color(30,30,30)); g.fillRect(facing==Direction.LEFT?x+9:x+19,y+9,3,3);
    }

    private void drawPerson(Graphics2D g,int tx,int ty,Color coat) {
        int x=tx*TILE,y=ty*TILE;
        g.setColor(new Color(235,200,150)); g.fillRect(x+9,y+5,14,10);
        g.setColor(coat); g.fillRect(x+7,y+15,18,13);
        g.setColor(new Color(40,40,40)); g.fillRect(x+8,y+27,7,5); g.fillRect(x+17,y+27,7,5);
    }

    private void drawHud(Graphics2D g) {
        g.setColor(new Color(20,24,36)); g.fillRect(490,12,138,62);
        g.setColor(Color.WHITE); g.setFont(new Font("Consolas",Font.BOLD,13));
        g.drawString(player.name,502,31);
        g.drawString("PV "+player.hp+"/"+player.maxHp,502,49);
        g.drawString("Badge "+badges,502,65);
        g.setColor(new Color(80,190,100));
        g.fillRect(502,54,100*player.hp/player.maxHp,4);
    }

    private void drawDialog(Graphics2D g) {
        g.setColor(new Color(20,24,36)); g.fillRoundRect(48,350,544,112,12,12);
        g.setColor(Color.WHITE); g.fillRoundRect(54,356,532,100,8,8);
        g.setColor(new Color(25,25,25)); g.setFont(new Font("Consolas",Font.BOLD,16));
        String visible=dialog.substring(0,Math.min(dialogChars,dialog.length()));
        for(int i=0;i<visible.length();i+=62) {
            int line=i/62,end=Math.min(i+62,visible.length());
            if(line<3) g.drawString(visible.substring(i,end),72,388+line*24);
        }
    }

    private void drawMenu(Graphics2D g) {
        g.setColor(new Color(20,24,36,220)); g.fillRect(0,0,W,H);
        g.setColor(Color.WHITE); g.fillRoundRect(420,30,190,250,10,10);
        g.setColor(new Color(35,35,35)); g.fillRoundRect(426,36,178,238,8,8);
        String[] items={"POKEDEX","PIKACHU","SAC","SAUVER","QUITTER"};
        g.setFont(new Font("Consolas",Font.BOLD,21));
        for(int i=0;i<items.length;i++){g.setColor(i==menuIndex?Color.YELLOW:Color.WHITE);g.drawString((i==menuIndex?"> ":"  ") + items[i],448,80+i*38);}
    }

    private void drawPokedex(Graphics2D g) {
        g.setColor(new Color(228,52,52)); g.fillRect(0,0,W,H);
        g.setColor(new Color(35,35,45)); g.fillRoundRect(50,35,540,410,20,20);
        g.setColor(Color.WHITE); g.setFont(new Font("Consolas",Font.BOLD,30)); g.drawString("POKEDEX",246,82);
        String[] names={"Pikachu","Bulbizarre","Salameche","Carapuce","Rattata","Noeunoeuf","Roucool","Abra","Abo","Nidoran","Mewtwo","Fantominus"};
        g.setFont(new Font("Consolas",Font.BOLD,20));
        for(int i=0;i<names.length;i++){
            int column=i%2,row=i/2;
            int lx=90+column*270,ly=130+row*54;
            g.setColor(seen[i]?Color.WHITE:new Color(130,130,130));
            String status=seen[i]?"#"+String.format("%03d",25+i)+"  "+names[i]+" "+(caught[i]?"[CAPTURE]":"[VU]"):"#"+String.format("%03d",25+i)+"  ??????";
            g.drawString(status,lx+35,ly);
            g.setColor(seen[i]?new Color(90,190,100):new Color(90,90,90)); g.fillOval(lx,ly-20,24,24);
            if(seen[i]){
                int[] ids={25,1,4,7,19,102,16,63,23,29,150,92};
                BufferedImage[] frames=pokemonSprites.get(ids[i]);
                if(frames!=null&&frames[0]!=null){
                    BufferedImage thumb=frames[0];
                    g.drawImage(thumb,lx-2,ly-26,46,46,null);
                }
            }
        }
        g.setColor(Color.YELLOW);g.setFont(new Font("Consolas",Font.BOLD,16));g.drawString("ECHAP pour revenir",205,414);
    }

    private void drawBattle(Graphics2D g) {
        g.setColor(new Color(242,240,210)); g.fillRect(0,0,W,H);
        g.setColor(new Color(160,195,120)); g.fillOval(350,270,210,65); g.fillOval(60,95,220,70);
        drawMonster(g,wild,435,180,false);
        drawMonster(g,player,185,300,true);
        drawStatus(g,wild,25,25,false);
        drawStatus(g,player,350,300,true);
        g.setColor(new Color(25,25,25)); g.fillRoundRect(25,390,590,75,10,10);
        g.setColor(new Color(245,245,225)); g.fillRoundRect(31,396,578,63,8,8);
        String[] options={"ATTAQUE","OBJET","CAPTURE","FUITE"};
        g.setFont(new Font("Consolas",Font.BOLD,20));
        for(int i=0;i<4;i++){
            int x=70+(i%2)*260,y=425+(i/2)*28;
            g.setColor(i==battleIndex?new Color(220,55,55):new Color(40,40,40));
            g.drawString((i==battleIndex?"> ":"  ") + options[i],x,y);
        }
    }

    private void drawStatus(Graphics2D g,Monster m,int x,int y,boolean playerSide) {
        g.setColor(new Color(30,30,30)); g.fillRoundRect(x,y,250,78,8,8);
        g.setColor(Color.WHITE); g.fillRoundRect(x+6,y+6,238,66,6,6);
        g.setColor(new Color(30,30,30)); g.setFont(new Font("Consolas",Font.BOLD,18));
        g.drawString(m.name+" Lv."+m.level,x+24,y+33);
        g.drawString("PV "+m.hp+"/"+m.maxHp,x+24,y+(playerSide?57:57));
    }

    private void drawMonster(Graphics2D g,Monster m,int x,int y,boolean playerSide) {
        BufferedImage sprite=pokemonSprite(m,playerSide);
        int desiredWidth=playerSide?160:128;
        int desiredHeight=playerSide?160:128;
        if(sprite!=null){
            double scale=Math.min(desiredWidth/(double)sprite.getWidth(),desiredHeight/(double)sprite.getHeight());
            int w=(int)Math.round(sprite.getWidth()*scale);
            int h=(int)Math.round(sprite.getHeight()*scale);
            int ox=x+(desiredWidth-w)/2;
            int oy=y+desiredHeight-h;
            g.drawImage(sprite,ox,oy,w,h,null);
        }else{
            Color color=new Color(240,205,55);
            g.setColor(color);
            g.fillOval(x+25,y+35,96,96);
        }
    }

    @Override public void actionPerformed(ActionEvent e) {
        if(screen==Screen.DIALOG&&dialogChars<dialog.length()) {
            long now=System.currentTimeMillis();
            if(now-dialogTick>18){dialogChars++;dialogTick=now;}
        }
        if(screen==Screen.WORLD){
            if(walking){
                float targetX=px*TILE,targetY=py*TILE;
                float dx=targetX-displayX,dy=targetY-displayY;
                if(Math.abs(dx)<=5&&Math.abs(dy)<=5){
                    displayX=targetX;
                    displayY=targetY;
                    walking=false;
                    if(heldDirection!=null)tryWalk(heldDirection);
                }else{
                    displayX+=Math.signum(dx)*Math.min(5,Math.abs(dx));
                    displayY+=Math.signum(dy)*Math.min(5,Math.abs(dy));
                }
            }
        }
        repaint();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int k=e.getKeyCode();
        if(screen==Screen.TITLE){if(k==KeyEvent.VK_ENTER||k==KeyEvent.VK_Z){screen=Screen.WORLD;loadSave();}return;}
        if(screen==Screen.POKEDEX){if(k==KeyEvent.VK_ESCAPE||k==KeyEvent.VK_Z)screen=Screen.MENU;return;}
        if(screen==Screen.DIALOG){if(k==KeyEvent.VK_Z||k==KeyEvent.VK_ENTER||k==KeyEvent.VK_SPACE){if(dialogChars<dialog.length())dialogChars=dialog.length();else screen=Screen.WORLD;}return;}
        if(screen==Screen.MENU){
            if(k==KeyEvent.VK_ESCAPE||k==KeyEvent.VK_X)screen=Screen.WORLD;
            else if(k==KeyEvent.VK_UP)menuIndex=(menuIndex+4)%5;
            else if(k==KeyEvent.VK_DOWN)menuIndex=(menuIndex+1)%5;
            else if(k==KeyEvent.VK_Z||k==KeyEvent.VK_ENTER)chooseMenu();
            return;
        }
        if(screen==Screen.BATTLE){battleKey(k);return;}
        if(k==KeyEvent.VK_ESCAPE){screen=Screen.MENU;menuIndex=0;}
        else if(k==KeyEvent.VK_UP){heldDirection=Direction.UP;tryWalk(Direction.UP);}
        else if(k==KeyEvent.VK_DOWN){heldDirection=Direction.DOWN;tryWalk(Direction.DOWN);}
        else if(k==KeyEvent.VK_LEFT){heldDirection=Direction.LEFT;tryWalk(Direction.LEFT);}
        else if(k==KeyEvent.VK_RIGHT){heldDirection=Direction.RIGHT;tryWalk(Direction.RIGHT);}
        else if(k==KeyEvent.VK_Z||k==KeyEvent.VK_ENTER)interact();
    }

    @Override public void keyReleased(KeyEvent e) {
        int k=e.getKeyCode();
        if(k==KeyEvent.VK_UP&&heldDirection==Direction.UP||k==KeyEvent.VK_DOWN&&heldDirection==Direction.DOWN||
           k==KeyEvent.VK_LEFT&&heldDirection==Direction.LEFT||k==KeyEvent.VK_RIGHT&&heldDirection==Direction.RIGHT)heldDirection=null;
    }

    private void tryWalk(Direction d) {
        if(walking||screen!=Screen.WORLD)return;
        facing=d;
        int nx=px+d.dx,ny=py+d.dy;
        if(nx<0||ny<0||nx>=MAP_W||ny>=MAP_H)return;
        Tile t=map[ny][nx];
        if(t==Tile.TREE||t==Tile.WATER||t==Tile.WALL||t==Tile.ROCK)return;
        px=nx;py=ny;
        walking=true;
        if(px>=20) currentRoute=2;
        else if(px>=14) currentRoute=1;
        else currentRoute=0;
        if(t==Tile.TALL&&random.nextInt(100)<12)startBattle();
    }

    private void interact() {
        int x=px+facing.dx,y=py+facing.dy;
        if(x==10&&y==16)showDialog("SOIN: Les creatures sauvages apparaissent dans les hautes herbes.");
        else if(x==19&&y==10)showDialog("MARCHAND: Achats disponibles au magasin de Jadielle.");
        else if(x==24&&y==8)startTrainerBattle();
        else if((x==19||x==20)&&y==10)buyFromShop();
        else if((x==19||x==20)&&y==11)buyFromShop();
        else if((x==5||x==6)&&y==18)healAtCenter();
        else if((x==24||x==25)&&y==6)enterArena();
        else if(x==14&&y==14)showDialog("PANNEAU: Route 1 - Jadielle a l'est.");
        else showDialog("En avant vers l'arene de Jadielle!");
    }

    private void healAtCenter() {
        player.hp=player.maxHp;
        showDialog("CENTRE POKEMON: Tes creatures sont de nouveau en pleine forme!");
    }

    private void buyFromShop() {
        if(coins>=100){
            coins-=100;
            potions++;
            healItemsBought++;
            showDialog("MARCHAND: Voici une potion! Solde: "+coins+" P.");
        }else showDialog("MARCHAND: Il te faut 100 P pour une potion.");
    }

    private void enterArena() {
        if(badges>=1)showDialog("MAITRE: Ton premier badge est deja reconnu.");
        else if(player.level>=7){
            badges=1;
            coins+=500;
            showDialog("MAITRE DE ARENE: Bravo! Tu obtiens le badge Volt!");
        }else showDialog("MAITRE: Reviens avec un Pokemon niveau 7 ou plus.");
    }

    private void showDialog(String text){dialog=text;dialogChars=0;dialogTick=System.currentTimeMillis();screen=Screen.DIALOG;}

    private void chooseMenu(){
        switch(menuIndex){
            case 0 -> screen=Screen.POKEDEX;
            case 1 -> showDialog(player.name+"  Niveau "+player.level+"  PV "+player.hp+"/"+player.maxHp);
            case 2 -> showDialog("SAC: Potions "+potions+"  Pokeballs "+balls+"  Argent "+coins);
            case 3 -> {saveGame();showDialog("Partie sauvegardee!");}
            case 4 -> screen=Screen.TITLE;
        }
    }

    private void startBattle(){
        String[][] encounters={
            {"Rattata","Roucool","Abo","Pikachu"},
            {"Abra","Rattata","Pikachu","Noeunoeuf"},
            {"Mewtwo","Fantominus","Abo","Nidoran"}
        };
        String[] names=encounters[Math.min(currentRoute,encounters.length-1)];
        int i=random.nextInt(names.length);
        String species=names[i];
        seen[pokedexIndex(species)]=true;
        trainerBattle=false;
        int levelBase=6+currentRoute*3+random.nextInt(3);
        wild=new Monster(species,14+currentRoute*4+random.nextInt(7),
                9+currentRoute*3+random.nextInt(4),
                6+currentRoute*2+random.nextInt(3),levelBase);
        battleIndex=0;
        screen=Screen.BATTLE;
    }

    private int pokedexIndex(String species){
        return switch(species){
            case "Pikachu" -> 0;
            case "Bulbizarre" -> 1;
            case "Salameche" -> 2;
            case "Carapuce" -> 3;
            case "Rattata" -> 4;
            case "Noeunoeuf" -> 5;
            case "Roucool" -> 6;
            case "Abra" -> 7;
            case "Abo" -> 8;
            case "Nidoran" -> 9;
            case "Mewtwo" -> 10;
            default -> 11;
        };
    }

    private void startTrainerBattle(){
        if(arenaDefeated){
            showDialog("DRESSER: Tu as deja gagne contre moi. Continue vers l'arene!");
            return;
        }
        seen[2]=true;
        wild=new Monster("Salameche",30,16,11,player.level+1);
        battleIndex=0;
        trainerBattle=true;
        screen=Screen.BATTLE;
    }

    private void battleKey(int k){
        if(k==KeyEvent.VK_UP||k==KeyEvent.VK_DOWN)battleIndex=(battleIndex+2)%4;
        else if(k==KeyEvent.VK_LEFT||k==KeyEvent.VK_RIGHT)battleIndex=battleIndex%2==0?battleIndex+1:battleIndex-1;
        else if(k==KeyEvent.VK_Z||k==KeyEvent.VK_ENTER)battleAction();
    }

    private void battleAction(){
        if(wild.hp<=0){winBattle();return;}
        switch(battleIndex){
            case 0 -> {
                double effectiveness=getEffectiveness(player.name,wild.name);
                int damage=Math.max(2,(int)((player.attack-wild.defense/2+random.nextInt(7))*effectiveness));
                wild.hp=Math.max(0,wild.hp-damage);
                if(wild.hp>0)wildAttack();else winBattle();
            }
            case 1 -> {if(potions>0){potions--;player.hp=Math.min(player.maxHp,player.hp+15);wildAttack();}else showDialog("Plus aucune potion!");}
            case 2 -> {if(balls>0){balls--;int chance=30+(100-wild.hp*100/wild.maxHp);if(random.nextInt(100)<chance){caught[pokedexIndex(wild.name)]=true;seen[pokedexIndex(wild.name)]=true;coins+=25;winBattle();}else wildAttack();}else showDialog("Plus aucune Pokeball!");}
            case 3 -> {if(random.nextInt(100)<70)screen=Screen.WORLD;else wildAttack();}
        }
    }

    private void wildAttack(){
        player.hp=Math.max(0,player.hp-Math.max(1,wild.attack-player.defense/3+random.nextInt(5)));
        if(player.hp==0){player.hp=player.maxHp;coins=Math.max(0,coins-100);screen=Screen.WORLD;}
    }

    private void winBattle(){
        player.exp+=wild.level*3;
        if(player.exp>=player.level*12){
            player.exp=0;player.level++;player.maxHp+=4;player.hp=player.maxHp;player.attack+=2;player.defense++;
        }
        if(trainerBattle){
            arenaDefeated=true;
            coins+=300;
            trainerBattle=false;
        }
        screen=Screen.WORLD;
    }

    private double getEffectiveness(String attacker,String defender){
        if(attacker.equals("Pikachu")&&(defender.equals("Carapuce")||defender.equals("Bulbizarre")))return 1.25;
        if(attacker.equals("Pikachu")&&defender.equals("Salameche"))return 0.85;
        if(attacker.equals("Pikachu")&&defender.equals("Pikachu"))return 1.0;
        return random.nextInt(100)<15?1.15:1.0;
    }

    private void saveGame(){
        Properties p=new Properties();
        p.setProperty("x",""+px);p.setProperty("y",""+py);p.setProperty("potions",""+potions);
        p.setProperty("balls",""+balls);p.setProperty("coins",""+coins);p.setProperty("level",""+player.level);
        p.setProperty("hp",""+player.hp);p.setProperty("maxHp",""+player.maxHp);
        p.setProperty("attack",""+player.attack);p.setProperty("defense",""+player.defense);
        p.setProperty("badges",""+badges);p.setProperty("arena",""+arenaDefeated);
        for(int i=0;i<seen.length;i++)p.setProperty("seen"+i,""+seen[i]);
        for(int i=0;i<caught.length;i++)p.setProperty("caught"+i,""+caught[i]);
        try(var out=Files.newOutputStream(SAVE)){p.store(out,"Pokemon Rouge");}catch(IOException ignored){}
    }

    private void loadSave(){
        if(!Files.exists(SAVE))return;
        Properties p=new Properties();
        try(var in=Files.newInputStream(SAVE)){p.load(in);px=Integer.parseInt(p.getProperty("x","9"));py=Integer.parseInt(p.getProperty("y","12"));
            potions=Integer.parseInt(p.getProperty("potions","3"));balls=Integer.parseInt(p.getProperty("balls","5"));
            coins=Integer.parseInt(p.getProperty("coins","500"));player.level=Integer.parseInt(p.getProperty("level","5"));
            player.maxHp=Integer.parseInt(p.getProperty("maxHp","25"));player.hp=Integer.parseInt(p.getProperty("hp","25"));
            player.attack=Integer.parseInt(p.getProperty("attack","14"));player.defense=Integer.parseInt(p.getProperty("defense","9"));
            badges=Integer.parseInt(p.getProperty("badges","0"));arenaDefeated=Boolean.parseBoolean(p.getProperty("arena","false"));
            for(int i=0;i<seen.length;i++)seen[i]=Boolean.parseBoolean(p.getProperty("seen"+i,i==0?"true":"false"));
            for(int i=0;i<caught.length;i++)caught[i]=Boolean.parseBoolean(p.getProperty("caught"+i,i==0?"true":"false"));
        }catch(IOException|NumberFormatException ignored){}
    }

    @Override public void keyTyped(KeyEvent e) {}

    private static class Monster {
        final String name;
        int level,maxHp,hp,attack,defense,exp;
        Monster(String name,int maxHp,int attack,int defense,int level){
            this.name=name;this.maxHp=maxHp;this.hp=maxHp;this.attack=attack;this.defense=defense;this.level=level;
        }
    }
}
